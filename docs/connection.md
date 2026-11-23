# Connection and discovery

**Status:** Step 5 implementation · **Related:** [`ux/ux-spec.md`](ux/ux-spec.md) §4.1, §6.1–6.3 · [`adr/0001-platform-and-stack.md`](adr/0001-platform-and-stack.md)

The connection gate is the only way into the app. It discovers WiThrottle servers, remembers layouts,
accepts manual addresses, and can start a hardware-free demo session. It owns no throttle logic.

## Module boundaries

| Layer | Responsibility |
| --- | --- |
| `:core:model` | `ServerEndpoint`, `SavedServer`, `DiscoveredServer`, `ConnectionTarget`, `ConnectionState`, and the user-safe `ConnectionError` taxonomy. No Android dependencies. |
| `:core:data` | `ServerDiscovery` (`NsdManager`), `SavedServerStore` (DataStore), `ConnectionRepository` (live socket and demo lifecycle), and `DemoTransport`. Protocol types stay below this boundary. |
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

With `targetSdk = 36`, Android uses `NEARBY_WIFI_DEVICES` as the temporary local-network permission
for nearby Wi-Fi discovery. The manifest declares it with `neverForLocation`. The single change
point is `LocalNetworkPermission`; when the app moves to `targetSdk = 37`, that object should switch
to `ACCESS_LOCAL_NETWORK` and the manifest must declare it.

The screen shows the rationale **before** the system dialog. It never requests automatically on
launch. If permission is denied, the UI shows an `Open settings` deep link to
`Settings.ACTION_APPLICATION_DETAILS_SETTINGS` and keeps manual entry available. On resume it
re-checks permission, so returning from Settings starts a scan without a restart.

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
contain separators or newlines without corrupting the store. Damaged records are skipped. A
successful live connection updates `lastConnectedEpochMillis` and creates the record if the
endpoint was discovered rather than saved. Demo sessions are never saved.

## Live session

`WiThrottleSession` owns the socket and runs on `Dispatchers.IO`:

1. connect with a 4-second timeout;
2. send `HU…` and `N…`;
3. wait up to 5 seconds for `VN`, mapping a version below 2.0 to `UnsupportedVersion`;
4. enable heartbeat monitoring and report `Connected`;
5. pump inbound lines, send a heartbeat every 5 seconds, and detect EOF.

Cancellation closes the socket from the caller side so a blocked read unblocks. An established
link that drops enters `Reconnecting` with capped exponential backoff (0.5 s → 8 s). An initial
failure goes to `Failed` with a typed error and a retry action; it is not retried forever.

All transport failures map to `ConnectionError`. Exception messages are never copied into UI copy.

## Demo mode

`DemoTransport` is an in-process, hardware-free session. It waits 450 ms to make the connecting
state visible, signals ready, and stays alive until cancelled. The connection screen and Drive both
show a persistent **Demo mode** banner while it is active. No socket, mDNS, or permission is used.

## Tests and previews

- Unit tests: saved-server codec, demo transport lifecycle, connection ViewModel (scan, permission,
  connect, failure, retry, manual validation, default port, demo, saved-row projection), and manual
  validation.
- Compose UI tests: scanning, empty, permission denied, connecting, failed, connected, and demo
  entry.
- `@CabPreview` variants cover scanning, empty, permission denied, connecting, failed, connected,
  and demo-connected states across the six device configurations.
- `:app` navigation test: Connect is the start destination, navigation is hidden on the gate, and
  demo mode is the route into Drive.

## Known limits

- mDNS remains best-effort; manual entry is the guaranteed path.
- Reconnect and heartbeat behaviour is implemented at the link level. Throttle-level re-acquire,
  speed reconciliation, and the 10 Hz command coalescer belong to Step 6.
- Diagnostics/log export, per-layout deletion confirmation, and permission rationale analytics are
  later-step work. There is no analytics.
