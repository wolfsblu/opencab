# Throttle engine

**Status:** Step 6 implementation · **Related:** [`protocol.md`](protocol.md) §6 · [`connection.md`](connection.md) · [`ux/ux-spec.md`](ux/ux-spec.md) §6.4–6.6, §7

The throttle engine is the domain layer between the UI and `DccClient`. It owns every slot's
desired state, acquisition, speed coalescing, functions, and link health. It contains no Compose,
Android view, socket, or timer-thread code.

## Module placement

| Layer | Responsibility |
| --- | --- |
| `:core:protocol` | Wire codec, typed commands/events, and the pure `SessionReducer`. |
| `:core:data` | `DccClient`, live transport, the `ThrottleController` engine, and `ThrottleSession`. |
| `:feature:throttle` | Compose drive screen over `ThrottleSession`; see [`drive-screen.md`](drive-screen.md). |

The engine reuses `SessionReducer` for acquisition, steal, movement, function, and power state. It
filters the reducer's handshake and heartbeat commands because `WiThrottleClient` already owns
those at the transport boundary. All server events and UI intents are serialized through one actor,
so commands reach the outbound channel in one order.

## Public API

```kotlin
val controller = ThrottleController(scope)

controller.uiState.collect { state ->
    state.slots.forEach { slot ->
        // slot.address, slot.speed, slot.functions, slot.health, ...
    }
}

controller.link(client)                 // ThrottleIntent.Link
controller.acquire(address, name = "341")
controller.setSpeed(address, 0.5)       // normalized 0.0..1.0
controller.nudge(address, steps = 1)
controller.setDirection(address, Direction.REVERSE)
controller.toggleFunction(address, 0)
controller.emergencyStop(address)
controller.release(address)
controller.unlink()                     // detach without releasing
```

`uiState` is an immutable `StateFlow<ThrottleUiState>`. The UI never imports a socket, protocol
command, or decoder. `ThrottleIntent` carries the same operations; the convenience methods are
thin wrappers around `dispatch(intent)`.

`ThrottleSession` (in `:core:data`) is the app-scoped attach point. It creates the controller,
follows `ConnectionRepository.activeClient`, links once per transport session, and exposes a
single-slot facade (`setSpeed`, `nudge`, `stop`, `setDirection`, function, steal, retry, release)
that keeps `LocoAddress` and `Direction` inside the data layer. The drive view model only sees
plain floats, booleans, and screen enums.

Each `ThrottleSlotUiState` exposes:

- `address` (`LocoAddress`) and `addressType`;
- optional `name` plus `displayName`;
- `speedSteps` (`14`, `28`, or `128`) and normalized `speed` (`0.0..1.0`);
- `step`, derived from `speed` and the active step mode;
- `direction`;
- functions `0..28` with label, on/off, momentary/latching, and enabled state;
- `acquisition`, `health`, `desired`, `emergencyStop`, and an optional `issue`;
- `canControl`, true only while online and acquired.

`ThrottleSlotUiState.health` is `ONLINE`, `STALE`, or `OFFLINE`. On disconnect the last known speed,
direction, and function state stay visible, but `health` becomes `STALE`, `canControl` becomes
false, and movement/function intents are ignored. The engine never reports a live state while the
link is down.

## Speed coalescing

- The first speed change in a quiet period is written immediately for a responsive throttle.
- Further changes are coalesced to one write per `100 ms` (configurable). The latest value always
  wins, so the final gesture value is guaranteed to be sent.
- A burst produces at most one speed write per interval; the UI is optimistic and renders the
  requested normalized speed immediately.
- `Stop` and `SetSpeed(0.0)` bypass the coalescer, clear any pending value, and send `V0` before
  the next coalesced write.
- `EmergencyStop` bypasses the coalescer, clears pending speed, and sends `X`. It sets only that
  slot's emergency-stop state; it never changes global track power.
- Direction, speed-step, function, acquire, and release commands are not coalesced.

The scheduler uses caller-supplied monotonic milliseconds plus coroutine `delay`. Tests pass the
virtual `TestScheduler.currentTime`, so every ordering assertion is deterministic.

## Acquisition and lifecycle

- `Acquire` marks the slot desired. It sends `M<key>+...` when the handshake is ready, and queues
  the slot when offline so a later connection acquires it.
- `LocoAdded` makes the slot controllable. `LocoRemoved` either removes a released slot or marks a
  still-desired slot `LOST`.
- `StealRequested` puts the slot in `IN_USE`, attaches `ThrottleIssue.InUse`, and disables control.
  `TakeControl` sends the steal confirmation and moves to `TAKING_CONTROL`; a later `LocoAdded`
  clears the issue.
- An acquire with no server answer within `acquireTimeoutMillis` (default 5 s) becomes `FAILED`
  with `ThrottleIssue.AcquireTimedOut`. It can be retried without dropping the link. A failed
  acquire on one slot does not affect another.
- A transport `send` failure is surfaced as `ThrottleIssue.CommandFailed` for that slot. Other
  slots continue.
- `Release` clears pending speed/function work, sends `V0` followed by `M-...`, and keeps the slot
  in `RELEASING` until the server removes it. Releasing a pending steal cancels the conflict.
- On disconnect, desired slots keep their last values as `STALE`; pending speed and momentary work
  is cancelled. On reconnect, every desired slot is automatically re-acquired. Re-acquisition is
  followed by an explicit `V0`, never the previous speed, because the layout may have stopped the
  train while the link was down.
- `Unlink` detaches the transport without releasing desired slots. A later `Link` re-acquires them
  on the new connection.
- `Link`/`Unlink` here mean transport attachment. App-side multi-throttle linking and consists are
  Step 9 work.

Track power is read-only state from `PPA`: it is exposed as `ThrottleUiState.trackPower` and shown
separately from per-loco emergency stop. Controls remain enabled while power is off, matching the
UX rule that track power is not ours to own.

## Functions

- Function numbers are `0..28` by default (`ThrottleConfig.functionRange`).
- Labels come from the server's `M...L...` message; missing labels fall back to `F<n>`.
- Latching functions toggle with `ForceFunction`.
- Momentary functions pulse on, then release automatically after `momentaryPulseMillis` (default
  250 ms). The UI can also use `SetFunction` for press-and-hold.
- Functions are enabled only while the slot is online and acquired; stale and out-of-range keys
  ignore input.
- Server `F`/`f`/`m` events overwrite local function state.

## Tests

`core/data/src/test/kotlin/dev/opencab/core/data/throttle` uses a fake `DccClient` and the
virtual-time test scheduler. Coverage includes:

- immediate first write, 10 Hz coalescing, trailing value, clamp and nudge;
- stop/emergency-stop priority and dropped pending values;
- cancellation of pending writes on release, disconnect, and scope cancellation;
- acquire, release ordering, conflict/take-control, timeout and retry;
- partial failure across slots, disconnect/stale, automatic re-acquire with `V0`;
- unlink/re-link, already-connected attach;
- function labels, latching, momentary pulse, disabled state, power/e-stop separation.

No test uses wall-clock sleeps or non-deterministic ordering.

## Known limits

- The drive screen uses one acquired slot. Multi-throttle, app-side linking, and consists are
  Step 9. Roster-driven acquisition now goes through `ThrottleSession.acquirePrimary`, which
  releases any previous desired slot and never duplicates the active address.
- Slots are in memory only; process-death restoration belongs to later steps.
- The engine uses the default WiThrottle throttle key and the reducer's default identity.
- Track power is status-only; `SetTrackPower` remains disabled in the reducer.
