# WiThrottle protocol layer

**Status:** Implemented in `:core:protocol` (Step 3)
**Scope:** Wire codec, typed commands/events, and a pure session reducer. No sockets, discovery,
threads, clocks, coroutines, or Android imports.

## 1. Sources

This document records the exact mappings implemented by the codec. Every syntax decision comes
from one of these sources:

1. [JMRI WiThrottle protocol page](https://www.jmri.org/help/en/package/jmri/jmrit/withrottle/Protocol.shtml)
   — the official user-facing protocol documentation and the source of the golden examples.
2. JMRI source, used to resolve inconsistencies in the prose documentation:
   - `java/src/jmri/jmrit/withrottle/MultiThrottle.java`
   - `java/src/jmri/jmrit/withrottle/MultiThrottleController.java`
   - `java/src/jmri/jmrit/withrottle/ThrottleController.java`
   - `java/src/jmri/jmrit/withrottle/DeviceServer.java`
   - `java/src/jmri/jmrit/withrottle/TrackPowerController.java`
   - `java/src/jmri/jmrit/withrottle/ConsistController.java`

Where the protocol page and the implementation disagree, the implementation and its examples
win. The disagreements are listed in [Known quirks](#9-known-quirks).

### 1.1 Module placement

The build-plan module map and this step both require the codec and the reducer in
`:core:protocol`. ADR-0001 originally proposed a separate `:protocol:withrottle` adapter and
`:core:session` reducer. Those remain valid refactor targets when a second command-station
protocol exists; creating them now would add ceremony without a second implementation. No
feature module imports `:core:protocol`.

## 2. Framing

WiThrottle is UTF-8 text, one message per line. The official page permits LF (`0A`), CR (`0D`),
or CRLF (`0D 0A`) as a terminator, and says clients must accept any of them.

`:core:protocol.codec.LineFramer` works on bytes, not characters, so:

- A multi-byte UTF-8 character split across two socket reads is decoded correctly once the frame
  completes.
- A partial frame remains buffered with no event emitted.
- Multiple frames in one read produce multiple events.
- CR and LF split across reads (CR at the end of one read, LF at the start of the next) produce one
  frame, not an empty one.
- Empty lines are ignored as framing noise.
- `reset()` discards a buffered partial frame, for use when reconnecting to a different server.

`WiThrottleDecoder` composes the framer with the line parser. It never throws for wire input:
unknown or malformed lines become `WiThrottleEvent.Unknown(raw, reason)` and are logged through
the `ProtocolLogger` seam. The default logger is a no-op; the Android layer can adapt it to
`Log`.

## 3. Value model

| Type | Wire form | Notes |
| --- | --- | --- |
| `ThrottleKey` | one character after `M` | Docs list `0`-`9`, `T`, `S`, `G`; JMRI accepts any non-delimiter character, so the codec preserves it. |
| `LocoAddress` | `S3` or `L341` | Short/long flag plus number. Codec accepts 0..10239; roster/UI layers own the stricter short/long ranges. |
| `LocoId` | `S3`, `L341`, or `*` | `*` means every loco on the throttle. |
| `LocoSelection` | `S3`, `L341`, or `E<roster id>` | Used by acquire. |
| `Direction` | `0` reverse, `1` forward | Any non-`0` server value is treated as forward. |
| `SpeedStepMode` | `1`, `2`, `4`, `8`, `16`, or a mode name | `1`=128, `2`=28, `4`=27, `8`=14, `16`=Motorola 28. Unknown names are preserved as `Other`. |
| `TrackPowerState` | `0` off, `1` on, `2` unknown | Only off/on can be requested. |
| `ProtocolVersion` | `VN` value | Ordered by major then minor; `raw` preserves the server text. |

## 4. Server-to-client messages (events)

`WiThrottleDecoder` maps complete lines to `WiThrottleEvent`. All examples below are parsed by
the tests.

| Wire | Example | Event | Notes |
| --- | --- | --- | --- |
| `VN<version>` | `VN2.0` | `Version` | Version 2.0 is the minimum accepted by the reducer. |
| `*<seconds>` | `*10` | `HeartbeatInterval` | Sent in reply to `N`; `0` means heartbeat is not required. |
| `PPA<state>` | `PPA1` | `TrackPower` | Decoded for display; v1 writes are disabled in the reducer. |
| `HT<type>` | `HTJMRI` | `ServerType` | Known types include `Digitrax`, `MRC`, `JMRI`. |
| `Ht<description>` | `HtJMRI 5.10 My Layout` | `ServerDescription` | Free text without newlines. |
| `HM<message>` | `HMaddress in use` | `Alert` | Error or alert text. |
| `Hm<message>` | `Hminfo` | `Info` | Informational text. |
| `PW<port>` | `PW12080` | `WebPort` | JMRI web-server port. |
| `RL<count>]\[<name>}\|{<addr>}\|{<S/L>...` | `RL1]\[SP 2101}\|{2102}\|{L` | `RosterList` | Declared count is preserved but not enforced. |
| `RCC<count>` | `RCC2` | `ConsistListHeader(editable=true)` | Make/break consists allowed. |
| `RCL<count>` | `RCL0` | `ConsistListHeader(editable=false)` | Selection-only list. |
| `RCD}\|{<consist>}\|{<id>]\[<loco>}\|{<true/false>...` | `RCD}\|{74(S)}\|{74(S)]\[3374(L)}\|{true` | `ConsistEntry` | One line per consist, not one line for the whole list. |
| `PFT<seconds><;><ratio>` | `PFT65871<;>4` | `FastClock` | Decoded for completeness; reducer ignores it. |
| `M<key>+<loco><;>` | `M0+L41<;>` | `LocoAdded` | Acquisition granted. |
| `M<key>-<loco><;>` | `M0-L341<;>` | `LocoRemoved` | Removal/release confirmation. |
| `M<key>S<loco><;><loco>` | `MTSL341<;>L341` | `StealRequested` | Server asks for a steal confirmation. |
| `M<key>L<loco><;>]\[<label>...` | `M0LL41<;>]\[Headlight]\[Bell]\[` | `FunctionLabels` | Labels are ordered by function number; a trailing empty element is preserved. |
| `M<key>A<loco><;>V<speed>` | `M0AL341<;>V23` | `SpeedChanged` | `V-126` (any negative value) means emergency stop. |
| `M<key>A<loco><;>R<0/1>` | `M0AL341<;>R1` | `DirectionChanged` | |
| `M<key>A<loco><;>s<mode>` | `M0AL341<;>s1` | `SpeedStepModeChanged` | |
| `M<key>A<loco><;>F<state><function>` | `M0AL341<;>F10` | `FunctionStateChanged` | First digit is state (`0`/`1`), remainder is the function number. |
| `M<key>A<loco><;>f<state><function>` | `M0AL341<;>f112` | `FunctionStateChanged` | Forced absolute state. |
| `M<key>A<loco><;>m<state><function>` | `M0AL341<;>m10` | `FunctionMomentaryChanged` | Sent in reply to `qm`. |

### 4.1 Unknown, malformed, and unsupported input

| Input | Result |
| --- | --- |
| Empty line | Ignored. |
| Unrecognised prefix | `Unknown(raw, "unknown message type")`, logged. |
| `PTT`, `PTL`, `PTA` | `Unknown(raw, "unsupported turnout message")`, logged. |
| `PRT`, `PRL`, `PRA` | `Unknown(raw, "unsupported route message")`, logged. |
| `PFC` | `Unknown(raw, "unsupported LnWI message")`, logged. |
| `RF`, `RS`, `RPF`, `RSF` | `Unknown(raw, "unsupported legacy throttle message")`, logged. |
| Recognised prefix with bad value | `Unknown(raw, "<specific malformed reason>")`, logged. |
| Unknown speed-step mode name | `SpeedStepModeChanged(..., Other(raw))`, not an error. |
| Roster count differs from entry count | Parsed; declared count is preserved. |
| Invalid UTF-8 | Replacement characters in an `Unknown` event; no exception. |

The reducer counts unknown events in `SessionState.unknownMessageCount` and otherwise ignores
them. No unknown input can terminate a session.

## 5. Client-to-server commands

`WiThrottleEncoder` maps typed commands to exact wire lines. `WiThrottleCommandDecoder` parses
the same canonical forms for round-trip tests and replay/diagnostic tooling. Encoded strings do
not include a line terminator; the transport layer appends the newline when it writes the socket.

| Wire | Example | Command | Notes |
| --- | --- | --- | --- |
| `N<name>` | `NOpenCab` | `SetDeviceName` | Must be the first identification command in practice. |
| `HU<id>` | `HUopencab-android` | `SetHardwareId` | Unique per device. |
| `*` | n/a | `Heartbeat` | Keeps the throttle alive. |
| `*+` | n/a | `EnableHeartbeatMonitoring` | Server starts its E-stop watchdog. |
| `*-` | n/a | `DisableHeartbeatMonitoring` | |
| `Q` | n/a | `Quit` | Ask the server to close the session. |
| `PPA0` / `PPA1` | n/a | `SetTrackPower(OFF/ON)` | Verified in `TrackPowerController`; `PPA2` is not settable. |
| `M<key>+<locoKey><;><selection>` | `M0+L341<;>L341`, `M0+S3<;>ED&RGW 341` | `Acquire` | Direct address or `E<roster id>` selection. |
| `M<key>-<loco><;>r` | `M0-L341<;>r` | `Release` | `M0-*<;>r` releases all locos. |
| `M<key>-<loco><;>d` | `M0-L341<;>d` | `Release(action=DISPATCH)` | Dispatch variant. |
| `M<key>S<loco><;><loco>` | `M0SL341<;>L341` | `Steal` | Reply to a `StealRequested`. |
| `M<key>A<loco><;>V<speed>` | `M0A*<;>V30` | `SetSpeed` | 0..126. |
| `M<key>A<loco><;>R<0/1>` | `M0AL341<;>R0` | `SetDirection` | |
| `M<key>A<loco><;>I` | `M0AL341<;>I` | `SetIdle` | Speed zero without E-stop. |
| `M<key>A<loco><;>X` | `M0AL341<;>X` | `EmergencyStop` | Per-loco E-stop; distinct from track power off. |
| `M<key>A<loco><;>F1<n>` | `M0AL341<;>F112` | `PressFunction` | Button down; JMRI toggles a latching function. |
| `M<key>A<loco><;>F0<n>` | `M0AL341<;>F012` | `ReleaseFunction` | Button up; turns a momentary function off. |
| `M<key>A<loco><;>f<0/1><n>` | `M0AL341<;>f112` | `ForceFunction` | Absolute state. |
| `M<key>A<loco><;>m<0/1><n>` | `M0AL341<;>m112` | `SetFunctionMomentary` | `1` momentary, `0` latching. |
| `M<key>A<loco><;>s<mode>` | `M0AL341<;>s2` | `SetSpeedStepMode` | |
| `M<key>A<loco><;>qV` | `M0A*<;>qV` | `QuerySpeed` | |
| `M<key>A<loco><;>qR` | `M0A*<;>qR` | `QueryDirection` | |
| `M<key>A<loco><;>qs` | `M0A*<;>qs` | `QuerySpeedStepMode` | |
| `M<key>A<loco><;>qm` | `M0A*<;>qm` | `QueryMomentaryStates` | |
| `M<key>A<loco><;>C<lead>` | `M0AL341<;>CL346` | `SetConsistLead` | Route functions to a consist lead. |
| `RC+<;><consist><;><name><:><loco><;><true/false>` | `RC+<;>S74<;>My consist<:>L341<;>true` | `ConsistAdd` | Creates a consist or adds a loco. |
| `RC-<;><consist><:><loco>` | `RC-<;>S74<:>L341` | `ConsistRemoveLoco` | |
| `RCP<;><consist><:><lead><;>...<;><trail>` | `RCP<;>S74<:>L346<;>L3374` | `ConsistSetOrder` | Order starts with the lead loco. |
| `RCR<;><consist>` | `RCR<;>S74` | `ConsistDelete` | |

### 5.1 Function semantics

The protocol page's `F` segment table says `0 = Push, 1 = Release`, but its examples and the JMRI
implementation are the reverse: `1` is the button-down/on state and `0` is button-up/off. This
codec follows the examples and source.

- `F1<n>` means the function button went down. JMRI **toggles** a latching function and sets a
  momentary function on.
- `F0<n>` means the button came up. JMRI turns a momentary function off and leaves a latching
  function alone.
- `f<0/1><n>` sets the absolute state and is the right command for a UI toggle that already knows
  the desired state.
- `m<0/1><n>` changes whether the function is momentary or latching.

The reducer mirrors those rules optimistically: a press toggles a latching function and turns a
momentary function on; a release only turns a momentary function off. Server `F`/`f`/`m` events
then replace local state with the server's view.

### 5.2 Acquire, steal, and release sequences

Granted acquisition:

```text
client  M0+L341<;>L341
server  M0+L341<;>
server  M0LL341<;>]\[Headlight]\[Bell]\[
server  M0AL341<;>F00
server  M0AL341<;>V0
server  M0AL341<;>R1
server  M0AL341<;>s1
```

Steal (requires JMRI 4.10 or later):

```text
client  M0+L341<;>L341
server  M0SL341<;>L341
client  M0SL341<;>L341
server  M0+L341<;>
```

Release:

```text
client  M0AL341<;>V0
client  M0-L341<;>r
server  M0-L341<;>
```

The reducer always sends `V0` before releasing an acquired loco, matching the safety rule in
ADR-0001.

## 6. Session reducer

`SessionReducer` is a deterministic state machine. It has no socket, thread, timer, clock, or
coroutine. Every input carries the caller's monotonic `atMillis`; a periodic caller invokes
`SessionInput.Tick(atMillis)`.

Inputs:

| Input | Meaning |
| --- | --- |
| `LinkUp(atMillis)` | The transport opened a socket. |
| `LinkDown(atMillis)` | The transport lost or closed the socket. |
| `Event(event, atMillis)` | A decoded server event. |
| `Intent(intent, atMillis)` | A higher-layer request. |
| `Tick(atMillis)` | A scheduling opportunity; may emit a heartbeat. |

### 6.1 Handshake

1. `LinkUp` emits `HU<hardwareId>` then `N<deviceName>`, sets `HandshakeState.AwaitingGreeting`,
   and clears server/heartbeat metadata.
2. `VN` below `SessionConfig.minimumProtocolVersion` (default 2.0) sets
   `HandshakeState.UnsupportedVersion` and blocks all control commands.
3. A supported `VN` sets `HandshakeState.Ready`. Desired locos are (re)acquired at that point.
4. Duplicate `VN` with the same version is idempotent; it does not re-send acquires.
5. Acquire intents issued before `Ready` are queued (`desired = true`, status `IDLE`) and sent when
   the handshake completes.

### 6.2 Heartbeat policy

- `*<seconds>` enables the policy when `seconds > 0`; `*0` disables it.
- With `HeartbeatPolicy.enabled` (default true), the reducer sends `*+` once to start server-side
  monitoring.
- A heartbeat is due after `max(minimumIntervalMillis, seconds * 1000 * fraction)`; defaults are
  1 second and 0.5 of the server interval. Half the interval leaves margin for Wi-Fi jitter.
- Any outbound command counts as activity and defers the next heartbeat, matching JMRI's
  "any contact keeps it alive" behaviour.
- `Tick` emits `*` when due; repeated ticks at the same timestamp do not duplicate it.
- `LinkDown` clears the heartbeat state; no ticks are emitted while disconnected.
- `SetHeartbeatEnabled` emits `*+`/`*-` explicitly.

### 6.3 Track power

`PPA` events update `SessionState.power`. `SetTrackPower` is a supported command in the codec but
the reducer ignores it by default: v1 is read-only. Set
`SessionConfig.allowTrackPowerControl = true` only after write support is verified. `PPA2`
(unknown) can never be requested.

### 6.4 Per-loco acquisition state

`SessionState.locos` is keyed by `LocoAddress`; each `LocoSlot` carries status, desired flag,
speed, direction, speed-step mode, E-stop, function state, labels, and speed provenance.

| Status | Meaning |
| --- | --- |
| `IDLE` | Known desired loco, not requested yet (for example, offline). |
| `REQUESTED` | Acquire sent, waiting for `M<key>+`. |
| `ACQUIRED` | Server granted the loco. |
| `STEAL_PENDING` | Server sent `M<key>S`; waiting for user confirmation. |
| `STEALING` | Client sent the steal confirmation. |
| `RELEASING` | Client sent release; waiting for `M<key>-`. |
| `LOST` | Link dropped or server removed the loco while it was still desired. |

Transitions that matter:

- `Acquire` while `IDLE`/`LOST` → `REQUESTED`; duplicate acquire is a no-op.
- `LocoAdded` → `ACQUIRED`; duplicate `LocoAdded` is a no-op.
- `StealRequested` → `STEAL_PENDING`; `ConfirmSteal` → `STEALING`; `LocoAdded` → `ACQUIRED`.
- `Release` → `SetSpeed(0)` + `Release`, status `RELEASING`, `desired = false`;
  `LocoRemoved` deletes the slot.
- Unexpected `LocoRemoved` while desired → `LOST` with `desired` preserved.
- `LinkDown` → active slots become `LOST`; `LIVE`/`COMMANDED` speed becomes `LAST_KNOWN`.
- `LinkUp` + supported `VN` re-acquires every desired slot, using the roster-entry selection when
  the slot was acquired that way.
- Released slots are not re-acquired.

### 6.5 Speed provenance

`LocoSlot.speedSource` makes stale values structurally visible:

| Source | Meaning |
| --- | --- |
| `UNKNOWN` | No speed information yet. |
| `COMMANDED` | Optimistically set by this app. |
| `LIVE` | Confirmed by a server echo. |
| `LAST_KNOWN` | Was `COMMANDED`/`LIVE`, but the link dropped. |

The reducer sends a non-zero duplicate speed only when the value or source differs; speed `0` is
always sent so an explicit stop is never suppressed. Server `V` events always win and become
`LIVE`. Per-loco `EmergencyStop` sends `X` and records speed 0 with `emergencyStop = true`.

### 6.6 Functions and consists

Function intents emit `F`, `f`, or `m` commands and update local state optimistically (see
§5.1). Server function events overwrite local state. Consist intents emit `RC+`, `RC-`, `RCP`,
`RCR`, and `C` commands and update an optimistic `SessionState.consists` cache; `RCC`/`RCD`
events replace that cache entry with the server's view. Server-side consisting remains a v1.1
feature; this layer only provides the protocol mechanics.

## 7. Version differences and gates

| Feature | Version / condition | Behaviour here |
| --- | --- | --- |
| `VN` handshake | All servers | Required before control commands. |
| Minimum accepted version | 2.0 (default) | Below 2.0 → `UnsupportedVersion`, no acquire/control. |
| `E` roster acquire | Added in protocol 1.7 | Supported (`LocoSelection.RosterEntry`). |
| `f` force function | Added in 2.0 | Supported. |
| `m` momentary setting | Added in 2.0 | Supported. |
| `q` query (`qV`, `qR`, `qs`, `qm`) | Added in 2.0 | Supported. |
| `s` speed-step mode | Added in 2.0 | Supported; unknown names preserved. |
| Steal (`S`) | Requires JMRI 4.10+ | Supported; reducer exposes a confirmation state. |
| `PPA` write | JMRI `TrackPowerController` | `PPA0`/`PPA1` supported by the codec; disabled by default in the reducer. |
| Fast clock `PFT` | Optional JMRI preference | Decoded, ignored by the reducer. |
| LnWI `PFC` | Vendor extension, purpose unknown | Unknown, logged. |
| Turnouts/points | Optional JMRI preference | Unsupported, logged as `Unknown`. |
| Routes | Optional JMRI preference | Unsupported, logged as `Unknown`. |
| Legacy `RF`/`RS`/`RPF`/`RSF` | Old T/S throttles | Unsupported, logged as `Unknown`. |
| Advanced-consist CV programming (`RCF`) | JMRI consist controller | Unsupported (v1 non-goal: no CV programming). |

## 8. Supported / unsupported matrix

| Capability | Decode | Encode | Reducer | Notes |
| --- | --- | --- | --- | --- |
| Version handshake | Yes | No (server-only) | Gate + metadata | `VN` |
| Heartbeat `/` `*+` / `*-` | Interval only | Yes | Scheduling policy | `*<seconds>` |
| Track power | Yes | On/off | Read-only by default | `PPA` |
| Acquire | Yes | Yes | State machine | `M…+…` |
| Release / dispatch | Yes | Yes | `V0` then release | `M…-…` |
| Steal | Yes | Yes | Confirm state | `M…S…` |
| Speed | Yes | Yes | Optimistic + provenance | `V` |
| Direction | Yes | Yes | State | `R` |
| Emergency stop | Yes (negative `V`) | Yes (`X`) | State | Per-loco, not track power |
| Functions | Yes (`F`/`f`/`m`) | Yes (`F`/`f`/`m`) | Optimistic toggle/momentary | 0..31 |
| Function labels | Yes | No (server-only) | Stored per loco | `M…L…` |
| Speed-step mode | Yes | Yes | State | `s` |
| Queries | No (server-only) | Yes | Emits `qV`/`qR`/`qs`/`qm` | 2.0+ |
| Consist list | Yes | No (server-only) | Cached | `RCC`/`RCL`/`RCD` |
| Consist commands | No (server-only) | Yes | Optimistic cache | `RC+`, `RC-`, `RCP`, `RCR` |
| Consist lead | No (server-only) | Yes | Emits `C` | `C<lead>` |
| Roster list | Yes | No | Stored for later import | `RL` |
| Server type/description/port | Yes | No | Stored | `HT`, `Ht`, `PW` |
| Alerts/info | Yes | No | Last message stored | `HM`, `Hm` |
| Fast clock | Yes | No | Ignored | `PFT` |
| Turnouts/points | No | No | `Unknown` | `PT*` |
| Routes | No | No | `Unknown` | `PR*` |
| CV programming | No | No | Rejected | `RCF` and `D` |
| Unknown top-level messages | No | No | Counted | Logged, never fatal |

## 9. Known quirks

1. **Function polarity in the docs.** The `F` table says `0 = Push, 1 = Release`; examples and
   `MultiThrottle.handleFunction` treat `1` as button-down/on and `0` as button-up/off.
2. **The `+` section header typo.** The prose header says `M<throttleId>A>…`; the example and
   implementation use `M<throttleId>+…`.
3. **Consist segment table order.** The example
   `RC+<;>S74<;>My consist<:>L341<;>true` is authoritative; the table columns are shuffled.
4. **Emergency stop is not `X` on the wire from the server.** Notifications use a negative speed
   (`V-126` in `MultiThrottleController.sendCurrentSpeed`). `X` is the client command.
5. **Function numbers are not zero-padded.** The first digit after `F`/`f`/`m` is the state; the
   remainder is the function number (`F10` = F0 on, `F01` = F1 off).
6. **Trailing `<;>` is normal.** `M0+…<;>` and `M0-…<;>` may have an empty action segment; the
   parser tolerates a missing separator as well.
7. **Consist addresses use `74(S)`/`3374(L)` in `RCD`**, while client commands use `S74`/`L341`.
   Both forms are accepted.
8. **Throttle keys are looser than documented.** The docs list `0`-`9`/`T`/`S`/`G`; JMRI accepts
   any character after `M`. The codec preserves any non-delimiter key.
9. **Roster counts are advisory.** A declared count that disagrees with the entries does not
   discard the line.
10. **Unknown speed-step names are not errors.** JMRI may encode newer modes by name; the codec
    preserves them in `SpeedStepMode.Other`.
11. **The first `RL` field is the roster entry id** in JMRI (`RosterEntry.getId()`), although the
    page calls it the name. It is exposed as `RosterLoco.name` and can be reused as a roster
    selection id when acquiring by roster entry.

## 10. Tests

Plain JVM tests live under `core/protocol/src/test/kotlin`:

- `LineFramerTest` — LF/CR/CRLF, every-byte split, CRLF across reads, UTF-8 split inside a frame,
  blank lines, reset, partial frames.
- `WiThrottleDecoderTest` — documented golden lines, the initial greeting, acquire/steal/release
  transcripts, speed/function/label parsing, roster/consist lists, metadata, unknown/malformed
  input, invalid UTF-8, duplicate events, Unicode, every-byte split, and a captured transcript
  (`src/test/resources/transcripts/jmri-acquire.txt`) fed whole and byte-by-byte.
- `WiThrottleEncoderTest` — golden encodings for every command, command-decoder round trips,
  validation failures, and malformed command lines.
- `SessionReducerTest` — handshake, version gate, queued acquire, heartbeat timing, power,
  metadata, quit, determinism, roster-entry acquire, version parsing.
- `SessionReducerLocoTest` — release, unexpected removal/reconnect, speed provenance, steal,
  speed/direction/functions/queries, labels, consist commands, consist events.

Run them with:

```bash
./gradlew :core:protocol:test
```

## 11. Deliberate non-goals

This layer does not open sockets, discover servers, run timers, persist state, or interpret CV
programming. It does not decide UI policy such as whether direction may change while moving; the
throttle engine in a later step owns that. It also does not implement turnout/route control,
fast-clock display, or legacy T/S throttle compatibility.
