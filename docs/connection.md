# Connection and discovery

**Status:** Steps 4–5 implementation · **Related:** [`ux/ux-spec.md`](ux/ux-spec.md) §4.1, §6.1–6.3 · [`adr/0001-platform-and-stack.md`](adr/0001-platform-and-stack.md)

The connection gate is the only way into the app. It discovers WiThrottle servers, remembers layouts,
and accepts manual addresses. It owns no throttle logic.

## Module boundaries

| Layer | Responsibility |
| --- | --- |
| `:core:model` | `ServerEndpoint`, `SavedServer`, `DiscoveredServer`, `ConnectionTarget`, `ConnectionState`, and the user-safe `ConnectionError` taxonomy. No Android dependencies. |
| `:core:data` | `DccClient` transport port, `WiThrottleClient` (socket, handshake, heartbeat, reconnect), `ServerDiscovery` (`NsdManager`), `SavedServerStore` (DataStore), and `ConnectionRepository` (link lifecycle). Protocol types stay below the feature boundary. |
| `:feature:connection` | `ConnectionViewModel`, immutable `ConnectionUiState`, the Compose screen, manual-entry sheet, previews, and UI tests. No sockets or protocol imports. |
| `:app` | Injects the app-scoped `ConnectionRepository`, starts the graph at `CONNECTION_ROUTE`, and makes Drive the visible destination after a successful connect. |

The app-scoped repository means a link survives navigation and configuration changes. Feature
ViewModels are route-scoped; only the repository owns the socket.

## State model

```text
Discovery:  Idle → Scanning → Complete(found, error?)
Link:       Disconnected → Connecting → Connected
                              └────────→ Failed(error)
                              └────────→ Reconnecting(attempt) → Connected
```
`DccConnectionState` in `:core:data` is this machine. `DefaultConnectionRepository` maps it onto the
user-facing `ConnectionState` without losing the target's name.

`ConnectionUiState` is immutable and contains:

- the local-network permission state;
- scan phase, discovered rows, and an optional discovery error;
- saved rows with `lastConnectedEpochMillis` and a row status;
- the active link (`Connecting`, `Connected`, `Reconnecting`);
- the last `FailureUiState` and its typed `ConnectionError`;
- the manual-entry sheet's editing state.

`ConnectionEvent.OpenDrive` is a one-shot channel event. It fires only on the transition into
`Connected`, so returning to the gate while already connected shows the connected card instead of
bouncing the operator back to Drive.

## Discovery

- Scans `_withrottle._tcp` on entry when local-network permission is granted.
- The browse is cancellable and time-boxed to **8 seconds** (`ServerDiscovery.SCAN_TIMEOUT_MILLIS`).
- Results are emitted progressively, de-duplicated by `host:port`, and kept in discovery order so
  rows do not jump around while the operator reads.
- An empty browse is a designed state, not an error: manual entry and the last-used layouts stay
  visible, and `Search again` is available.
- Discovery failure is a typed error with its own copy. It never surfaces an `NsdManager` code.
- The UI includes no pull-to-refresh and no subnet scanning.

## Local-network permission

Verified against Android's [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission)
guidance (current as of November 2026):

- **`targetSdk = 36` (this app):** local-network access is implicitly granted through `INTERNET`.
  Do **not** declare or request `ACCESS_LOCAL_NETWORK`. The deprecated Android 16 opt-in used
  `NEARBY_WIFI_DEVICES`; it is not the local-network permission and is no longer declared.
- **`targetSdk = 37` or higher (future):** declare and request
  `android.permission.ACCESS_LOCAL_NETWORK` before NsdManager discovery or local TCP traffic.

`LocalNetworkAccess` in `:core:data` is the single boundary check. `LocalNetworkPermission` in
`:feature:connection` delegates to it; at target 36 it returns “granted” without showing a dialog.
The transport still maps a blocked NsdManager call (`SecurityException`) or socket failure
(`EPERM`/permission denied) to `ConnectionError.LocalNetworkPermissionDenied`. No layer requests a
permission or shows raw system text.

## Manual entry

- Fields: optional name, host, port. Port defaults to **12090**.
- Host uses `KeyboardType.Uri` with autocorrect off; port uses `KeyboardType.Number`.
- Validation is a pure function (`ManualServerValidator`) with inline, field-level errors:
  required host, host without scheme/spaces/path, numeric port in 1–65535.
- A new manual entry is saved and connected immediately. Editing an existing saved layout updates
  the record without silently changing the active link.
- Host validation never performs DNS resolution; resolution happens at connect time.

## Saved layouts

DataStore holds a small line-oriented record set. Each field is URL-safe base64, so names may
contain separators or newlines without corrupting the store. Records are validated through
`SavedServer`/`ServerEndpoint`: id and name are non-blank, the host is a plain address (no scheme,
path, whitespace, or embedded port), and the port is 1–65535. The default port is **12090**. Damaged records are
skipped. A successful live connection updates `lastConnectedEpochMillis` and creates the record if
the endpoint was discovered rather than saved. WiThrottle has no credentials, so none are stored.

## Live session

`DccClient` is the transport port: `connect`, a bounded outbound command channel (`send`/
`trySend`), an inbound `Flow<WiThrottleEvent>`, `state`, and `close`. `WiThrottleClient` owns one
socket session and runs on `Dispatchers.IO`:

1. `Connecting`, select the Wi-Fi/Ethernet network and bind the socket to it, then TCP connect with
   a 4-second timeout. This is required when a layout's access point has no internet: Android
   keeps cellular as the default network, so an unbound socket would otherwise leave through
   cellular and never reach `192.168.4.x`. Hostnames are resolved through the selected network.
2. Send `HU…` and `N…`, read bytes through the incremental decoder, and wait up to 5 seconds for
   `VN`. A version below 2.0 becomes `UnsupportedVersion`; a non-WiThrottle message becomes
   `ProtocolMismatch`.
3. `Connected`, send `*+`, and start three coroutines: command writer, heartbeat sender, and
   liveness watchdog.
4. A partial frame at EOF is discarded, never emitted as a complete message.
5. An established link that drops enters `Reconnecting` with capped exponential backoff and
   jitter (base 500 ms, ×2, capped at 8 s, 50–100 % of the delay). Retryable failures keep
   retrying while the operator stays; permission loss, version mismatch, and protocol mismatch go
   straight to `Failed`.
6. An initial failure goes to `Failed` with a retry action; it is not retried forever.

The heartbeat watchdog handles JMRI's quiet-server behaviour. It sends `*` at half the negotiated
cadence, which is inside JMRI's E-stop timer even when its timer fires early. After `2 ×` the heartbeat interval with no inbound traffic, it sends `N<deviceName>`,
which forces JMRI to reply with `*<seconds>`. If that reply misses a 3-second probe window, the
socket is closed, the session becomes `Reconnecting`, and the read loop unblocks.

Cancellation and `close()` close the socket, close the command channel, cancel the session job,
and write `Disconnected`. `close()` is idempotent and safe to call concurrently.

All transport failures map to `ConnectionError` (`TimedOut`, `ServerClosed`, `Unreachable`,
`ConnectionRefused`, `HostNotFound`, `LocalNetworkPermissionDenied`, `UnsupportedVersion`,
`ProtocolMismatch`, `NotWiThrottle`, `Unknown`). Exception messages are never copied into UI copy.

## Tests and previews

- Unit tests: saved-server codec and validation, reconnect policy, connection repository state
  mapping, connection ViewModel (scan, permission, connect, failure, retry, manual validation,
  default port, saved-row projection), and manual validation.
- Fake-TCP-server tests: dropped connection mid-frame (no partial event), heartbeat timeout,
  reconnect, concurrent close, invalid handshake, old protocol version, cancellation, and an
  acquire round trip.
- Compose UI tests: scanning, empty, permission denied, connecting, failed, and connected.
- `@CabPreview` variants cover scanning, empty, permission denied, connecting, failed, and connected
  states across the six device configurations.
- `:app` navigation test: Connect is the start destination, navigation is hidden on the gate, and
  manual entry opens from the gate.

## Known limits

- mDNS remains best-effort; manual entry is the guaranteed path. `NsdManager` may still browse on
  the platform default network when a layout AP has no internet, so a discovered result is not
  guaranteed on every AP. The socket itself is bound to Wi-Fi/Ethernet (`AndroidNetworkBinder`).
- Reconnect and heartbeat behaviour is implemented at the link level. Throttle-level re-acquire,
  speed reconciliation, and the 10 Hz command coalescer live in the `ThrottleController` engine
  described in [`throttle-engine.md`](throttle-engine.md). The outbound channel is bounded and drops
  commands while disconnected; the engine owns re-acquire and coalescing.
- Diagnostics/log export, per-layout deletion confirmation, and permission rationale analytics are
  later-step work. There is no analytics.
