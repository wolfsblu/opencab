# ADR-0001: Platform and stack

- **Status:** Accepted
- **Date:** 2026-11-20
- **Deciders:** Product, Android
- **Supersedes:** —
- **Related:** [`../product-brief.md`](../product-brief.md) · [`../ux/ux-spec.md`](../ux/ux-spec.md)

---

## 1. Context

OpenCab is a cab for DCC model railways. It talks to a command station over WiThrottle (JMRI and its dialects), on a LAN, from a phone held in one hand, while the operator watches a physical train.

The constraints that actually shape the stack:

1. **The throttle is a real-time control surface.** A 150 ms lag causes over-correction; a janky frame during a drag reads as a stutter in the train. Rendering and touch handling are safety-relevant, not cosmetic.
2. **The link is part of the product state.** WiThrottle is a line-oriented socket protocol with server-side heartbeat E-stops, asynchronous `M` notifications, steal prompts, and no request/response correlation for speed. "Connected" is a first-class domain value, not an exception path.
3. **Material 3 Expressive is a genuine fit**, not a fashion choice: its hierarchy research (key elements located up to 4× faster) maps directly onto a glance-read control surface, and its components (`ShortNavigationBar`, `LoadingIndicator`, `ButtonGroup`, shape morphing, motion schemes) cover most of what we would otherwise hand-build.
4. **A second protocol is planned but not funded.** The abstraction must make it *possible* without paying today's cost for it.
5. **There is no backend and there will not be one in v1.** No accounts, no sync, no server-side state. All identity is local.

## 2. Decision

**Kotlin + Jetpack Compose + Material 3 Expressive, a single Activity, a modularised codebase with a protocol-agnostic domain port, and WiThrottle as the first (and only) adapter in v1.**

### 2.1 Platform

| Choice | Value | Rationale |
|---|---|---|
| Language | Kotlin, latest stable | Coroutines/Flow are the right model for a socket + UI state; no alternative is close |
| UI | Jetpack Compose only (no XML layouts) | The throttle is a custom-drawn, gesture-defined control; Compose is the shortest path and avoids View interop seams |
| Design system | Material 3 (`androidx.compose.material3` 1.5.x) with `MaterialExpressiveTheme` | Native expressive components and physics-based motion, no reimplementation |
| Build | Gradle KTS + version catalog + convention plugins | Eleven modules is fine; eleven copies of `android { }` is not |
| minSdk | 26 | Club spare phones skew old; nothing in the design needs newer |
| targetSdk | 36 | Android 16 local-network rules must be designed for, not discovered in production |
| DI | Manual `AppContainer` + constructor injection | ~10 graphs; no annotation processor cost. Revisit at 3+ contributors or when scopes get non-trivial |
| Persistence | DataStore (Proto or Preferences) | Recents, favourites, settings, stable device ID. No relational data exists; Room would be ceremony |
| Serialization | kotlinx.serialization | For DataStore payloads only |
| Async | Coroutines + `StateFlow`; `Channel(CONFLATE)` for commands | Conflation *is* the "latest speed wins" rule from the UX spec |
| Networking | `java.net.Socket` + `BufferedSource`, no HTTP stack | It is a raw line protocol; OkHttp/Retrofit/kotlinx-io would all be overhead or a poor fit |
| Discovery | `NsdManager` (`_withrottle._tcp`) | Platform API; best-effort by design, manual entry is first-class |

Explicitly **not** in the stack: Hilt, Room, Retrofit, a navigation framework beyond Navigation-Compose, a third-party throttling/knob widget, an analytics SDK, Firebase, a foreground service.

### 2.2 Single Activity

One `ComponentActivity` hosting a Compose `NavHost`. No per-screen activities, no fragments.

Why: the drive screen must survive a link drop without being destroyed or recreated, the connect→drive transition is a shared-element morph (impossible across activities without pain), and there is no deep-link surface or system integration that would justify separate tasks. Window insets, predictive back, and the keep-screen-on flag are handled once, in the Activity.

**Cost accepted:** no per-screen task affinity, no separate launch entry points, and the Activity owns lifecycle concerns that would otherwise be distributed. None of these matter for a two-level navigation graph.

### 2.3 Module boundaries

```
┌──────────────────────────────────────────────────────────┐
│ shell          :app                                      │
├──────────────────────────────────────────────────────────┤
│ features       :feature:connect · drive · roster ·       │
│                settings                                  │
├──────────────────────────────────────────────────────────┤
│ presentation   :core:ui · :core:session                  │
├──────────────────────────────────────────────────────────┤
│ ports          :core:protocol                            │
├──────────────────────────────────────────────────────────┤
│ adapters       :protocol:withrottle (wire)               │
│                :core:data (local store)                  │
├──────────────────────────────────────────────────────────┤
│ domain         :core:model   (depends on nothing)        │
└──────────────────────────────────────────────────────────┘
```

Dependencies point **downward**: a layer may depend on the layers below it, never above. The table below is the precise, per-module contract; the diagram is the shape.

| Module | Responsibility | May depend on |
|---|---|---|
| `:core:model` | Pure Kotlin domain types: `LocoAddress`, `SpeedStep`, `Direction`, `Loco`, `ServerEndpoint`, `LinkState`, `SessionState`. No Android imports. | — |
| `:core:protocol` | **Ports only.** `LayoutGateway`, `LinkState`, `CommandSink`, error taxonomy (`AcquireDenied`, `BlockedByHolder`, `VersionUnsupported`, `TransportFailure`). No I/O, no wire strings. | `:core:model` |
| `:protocol:withrottle` | The adapter: TCP socket, line codec, `NsdManager` discovery, heartbeat, `M`/`MTS`/`HM` parsing, power and roster mapping. | `:core:protocol`, `:core:model` |
| `:core:session` | State holders and pure reducers, reconnect policy, command coalescing, speed reconciliation, one source of truth for `SessionState`. | `:core:protocol`, `:core:model` |
| `:core:data` | DataStore repositories: recent layouts, favourites, settings, stable device ID. | `:core:model` |
| `:core:ui` | `MaterialExpressiveTheme`, semantic + dynamic colour, type scale, the throttle canvas and Stop/Direction controls, status components, shared motion tokens. | `:core:model` |
| `:feature:connect` / `drive` / `roster` / `settings` | Screen + ViewModel + UI state mapping. No wire knowledge, no sockets. | `:core:session`, `:core:ui`, `:core:model` (+ `:core:data` for roster/settings) |
| `:app` | Activity, nav graph, `AppContainer`, insets, keep-awake, window size class injection. | everything |

**Dependency rules (enforced by a Gradle check, not by convention):**

1. Features **never** import `:protocol:*`.
2. `:protocol:*` **never** imports `:core:ui` or any feature.
3. `:core:model` has zero Android dependencies.
4. Only `:core:session` may hold mutable session state.
5. Only `:app` wires concrete implementations to ports.

### 2.4 The protocol port

The UI and the session layer speak a small, stable vocabulary. Everything else is the adapter's problem.

| Domain concept | WiThrottle realisation | Note |
|---|---|---|
| `LinkState` | socket + heartbeat | `Connecting`, `Live(lastHeard)`, `Degraded`, `Lost`, `Blocked(version)` |
| `StationProfile` | `VN`, `HT`, `Ht`, `PPA`, `RL` | Version gate applied here, once |
| `acquire(address)` → `Granted \| BlockedByHolder \| Denied \| Unsupported` | `M<k>+<addr>`, `MTS…`, `HM…` | The steal prompt becomes a **domain outcome**, not a UI string |
| `setSpeed(step)` | `M<k>A<addr><;>V<n>` | Fire-and-forget; reconciled (§2.6) |
| `setDirection` | `<;>R<0\|1>` | Only sent when the session is at 0 |
| `stop` | `<;>V0` | Never `X`; E-stop is a separate, explicit capability |
| `release` | `M<k>-<addr><;>r` | Preceded by `V0` |
| `setFunction` | `<;>F`/`<;>f` (momentary/latching) | Labels from `M<k>L` |
| `trackPower` | `PPA0/1/2` | **Read-only** in v1 |

Why this shape: WiThrottle's own vocabulary — throttle keys, `*` broadcast, slot IDs, `MTS` — is a *transport* dialect. Leaking it upward is how an app ends up unable to support a second command station, and how a UI ends up asking a user to pick a "throttle ID". The domain vocabulary is chosen so that a Z21/ECoS/LocoNet-over-TCP adapter can implement the same port without touching a single composable.

### 2.5 State model

One `SessionState` `StateFlow`, produced by a pure reducer over domain events, is the only thing the UI reads.

```
inbound wire ──► codec (pure) ──► DomainEvent ──► reducer (pure) ──► SessionState
                                                     ▲
user intent ──► Feature VM ──► Intent ───────────────┘
                                     │
                                     └──► CommandSink (CONFLATE) ──► codec (pure) ──► socket
```

Two type-level rules that exist because P3 ("never lie about state") is otherwise unenforceable in review:

- **Speed is either confirmed or last-known.** `SessionState.speed` carries provenance (`Live(at: Instant)` / `LastKnown(value, at: Instant)` / `Unknown`). The UI cannot render a live-looking number from a stale value, because the type does not offer one.
- **A loco is either held or not.** `Held(handle)` is a distinct state from `Acquiring` and `Blocked`. No composable can render "driving" without a handle.

### 2.6 Truthfulness against a protocol with no acknowledgements

WiThrottle has no request/response correlation for `V`. Our speed command may be echoed (`M<k>A…V<n>`), may be ignored, or may be overridden by another throttle.

Policy:

1. Command optimistically; show the commanded value immediately (UX §7.6).
2. Accept a server echo as confirmation.
3. If no echo within 500 ms of the drag ending, probe with `qV` and reconcile.
4. On any inbound `V` that disagrees with our commanded value while we hold the loco, adopt the server's value and mark it `Live` — the layout is the truth, not the phone.
5. On link loss, every `Live` value becomes `LastKnown` in one transition; the UI has no code path that can skip it.

### 2.7 Things we intentionally deferred

| Deferred | Why now | When |
|---|---|---|
| Foreground service | You cannot drive a train you cannot see. Keep-screen-on covers the real case; a background-driving service adds notification, battery, and lifecycle complexity for a capability we do not want. | If a wearable or hardware-throttle scenario appears |
| Multi-loco / consists | One cab, one train (product brief A6). The port can grow a second handle later. | v1.1 |
| Second protocol | Unfunded. The port is the option, not the implementation. | When a layout demands it |
| Room / SQLDelight | No relational data. | Probably never |
| Hilt | Build cost for ~10 graphs. | 3+ contributors |
| KMP shared core | Would force the whole stack into a multiplatform shape for an iOS product we are not building. | If iOS is greenlit, share `:core:model` + `:core:protocol` + `:protocol:withrottle`, not the UI |

## 3. Consequences

### Positive

- **Protocol swappability with a bounded blast radius.** One module implements a port; no composable knows what a throttle key is.
- **The codec is a pure function**, so it is testable against golden strings lifted straight from the JMRI protocol documentation — no emulator, no layout.
- **Truthfulness is structural.** Stale speed and "held" state are unrepresentable in the wrong form, so review does not have to catch them.
- **Expressive churn is contained.** `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` appears in exactly one module; a Compose upgrade that breaks a component touches `:core:ui`, not every screen.
- **Rendering budget is defensible.** One custom `Canvas` for the arc, no nested scroll on Drive, no animation loop, `derivedStateOf` for the thumb position. The 16 ms frame budget is achievable and measurable with Macrobenchmark.

### Negative

- **Eleven modules for a v1 hobby app** is more Gradle ceremony than the feature set strictly needs. Mitigated by convention plugins; the payoff is the codec/adapter seam, which is the one seam we know we will need.
- **Compose-only** means no off-the-shelf rotary knob and no XML widget reuse. The throttle must be built and tested as a first-class custom control, including gesture fuzzing.
- **M3 Expressive APIs are still partially experimental** at 1.5.x. Pinning the BOM and wrapping components is a real maintenance line item, not a one-off.
- **No foreground service** means the app cannot usefully drive in the background. Accepted deliberately; the alternative (a train moving with no visible UI) is worse than the limitation.
- **WiThrottle dialect quirks leak**, but only into the adapter. JMRI, LNWI, and MRC differ in heartbeat behaviour and greeting completeness; the version gate at `VN` plus feature-detection is the containment strategy.
- **Single Activity** couples window-level concerns to one class. Acceptable at this navigation depth; would need revisiting for deep linking or multi-window per-screen.

### Risks

| Risk | Mitigation |
|---|---|
| mDNS flakiness (documented by JMRI itself) | Manual entry is a first-class path, recents make it one tap; discovery failure is a designed state, not an exception |
| Android 16 local-network permission blocks sockets, not just discovery | Runtime permission flow designed in the Connect gate; tested on API 36 hardware before alpha |
| Heartbeat E-stop on a Wi-Fi blip stops the train | Correct-by-default (safety), softened by the reconnect UX; `*+` on by default with a Settings escape hatch |
| Expressive motion implying physical motion | Scoped `MotionScheme.standard()` around the throttle/readout; documented in UX §1.3 |

## 4. Rejected alternatives

### 4.1 Flutter

**Case for:** one codebase for Android and iOS; fast iteration; good custom painting.

**Why rejected:** Material 3 Expressive is a first-party, native design system with native components and a native motion-physics implementation. In Flutter it would be a hand-port: shape morphing, motion schemes, `ShortNavigationBar`, `LoadingIndicator`, expressive type roles — all rebuilt, all diverging with each Material release. The only durable argument for Flutter is cross-platform, and iOS is explicitly out of v1 scope. If iOS is ever greenlit, the cheap path is a shared Kotlin core (model + protocol) behind a native SwiftUI shell, not a UI rewrite in Dart.

**Reversibility:** low. This is the decision most expensive to undo.

### 4.2 Web wrapper / PWA / Trusted Web Activity

**Case for:** no install friction, works on any phone, trivial updates.

**Why rejected:** every capability this product depends on is worse in a WebView — raw TCP sockets (unavailable without a native shim, which reintroduces the native app), mDNS (unavailable), reliable screen wake (unreliable), haptics (coarse or absent), and touch-drag latency on a 60 Hz compositor (directly violates the 150 ms finger→motion budget). Also fails the club distribution model: a QR code to a PWA is fine until the club Wi-Fi has no internet.

**Reversibility:** low in practice; the only escape hatch is a rewrite.

### 4.3 Protocol-first UI

**Case for:** fastest to build against the protocol; no translation layer; a great debugging tool.

**Why rejected:** it produces a terminal, not a cab. WiThrottle's vocabulary (throttle keys, `*`, `MTS`, slot semantics) would surface in the UI, address entry would become the primary flow, and the second protocol would require rewriting the UI instead of adding an adapter. It also makes the steal prompt a string instead of a domain outcome, which is precisely the case where the product's honesty matters most.

**Reversibility:** medium, but it would have to be undone anyway.

### 4.4 Other rejected options

| Option | Why not |
|---|---|
| **Kotlin Multiplatform now** | Forces a multiplatform shape onto a single-platform product; the only shareable code is the core, which our modularisation already isolates |
| **XML Views + Compose interop** | Two rendering paths, two theming systems, and no benefit for a custom gesture surface |
| **Third-party knob/slider library** | The throttle's contract (delta-drag, no jump, cancel-on-drag, hold-to-run) is the product; a general-purpose widget cannot express it, and we would fight it forever |
| **MVI framework (Orbit, MVIKotlin)** | We have one stateful screen; plain reducers + `StateFlow` are less machinery than a framework and equally testable |
| **Hilt from day one** | Real but modest benefit at this size; build-time cost is immediate, the benefit is not |
| **Room** | No relational data |
| **Firebase / analytics SDK** | Contradicts "no accounts" and the offline-first club context; Play vitals gives crash-free rate without an SDK |

## 5. Verification

The decision is only real if these exist by the end of Step 1:

1. **Codec golden tests** using the example strings in the JMRI protocol documentation (`VN2.0`, `RL2]\[…`, `MTSL341<;>…`, `PPA0`, `*10`).
2. **Reducer truthfulness tests:** every `LinkState` transition asserts speed provenance degrades to `LastKnown`; no state can be `Held` while `Lost`.
3. **Forced-drop harness:** kill the socket at 0%, mid-drag, and with a sheet open; assert the reconnect sequence re-acquires the same address and restores speed to 0.
4. **Dependency check** in CI: features cannot see `:protocol:*`.
5. **Macrobenchmark:** cold start, and frame timing during a 10 s drag (target: no frame > 32 ms).
6. **Screenshot tests** for `:core:ui` at 320/360/600/840 dp, light/dark, 100%/200% font.

## 6. Follow-ups for Step 1

- Confirm package name (`dev.opencab` is a placeholder) before the first signed build.
- Decide whether `:core:session` and `:core:data` merge (they are small); keep separate until the reducer earns its own module.
- Decide the `*+` heartbeat default with two club operators (product brief open question 1).
- Confirm Android 16 local-network permission behaviour on real hardware before committing to the Connect-gate copy.

---

## Addendum: design system implementation (Step 2)

- **Material3 version.** The pinned Compose BOM (`2026.06.01`) resolves `androidx.compose.material3`
  **1.4.0**, not the 1.5.x line assumed above.
- **Expressive theme and motion are internal in 1.4.0.** `MaterialExpressiveTheme`, `MotionScheme`,
  `MotionScheme.expressive()`, `MotionScheme.standard()`, and `MaterialTheme.motionScheme` are
  declared `internal`. They cannot be called from `:core:designsystem`.
- **Consequence.** `CabTheme` is built on the public `MaterialTheme(colorScheme, typography,
  shapes)` overload. `CabMotion` mirrors Material's generated expressive and standard spring token
  values and is exposed through `LocalCabMotion` / `CabTheme.motion`. Component call sites do not
  depend on the missing API, so they can switch to the public motion scheme when it lands.
- **What is available.** Expressive navigation components (`ShortNavigationBar`,
  `WideNavigationRail`, the `NavigationSuiteType` layout values), `TextAutoSize`, and the
  `Confirm` / `Reject` / `SegmentTick` haptic types are public and are used.
- **Verification.** The six-variant `@CabPreview` matrix, Compose semantics instrumentation tests,
  and WCAG contrast unit tests are the acceptance surface. No screenshot-test harness is configured
  in this project; AGP 9's screenshot-test plumbing requires the external preview-renderer plugin,
  which is not wired in.

## Addendum: protocol implementation placement (Step 3)

- **Decision.** Step 3 implements the WiThrottle line codec, typed messages, and the pure session
  reducer in `:core:protocol`, following the build-plan module map and the explicit Step 3
  instruction. The reducer is deterministic and takes caller-supplied timestamps; it contains no
  sockets, threads, clocks, or coroutines with real time.
- **Divergence from §2.3.** The ADR proposed `:protocol:withrottle` for the wire adapter and
  `:core:session` for the reducer. Those modules were not created. With one protocol and one
  session core, the extra Gradle modules would add ceremony without a second implementation.
- **Revisit when.** A second command-station protocol (Z21, LocoNet, or similar) is funded, or a
  second session consumer needs to share the reducer. At that point the codec can move to a
  `:protocol:withrottle` adapter and the reducer to `:core:session`; the public types in
  `:core:protocol` are already transport-free.
- **Verification.** `docs/protocol.md` records the exact wire mappings and the supported/unsupported
  matrix. Plain JVM tests cover golden messages, captured transcripts, every-byte chunk splits,
  round trips, malformed input, duplicates/unknowns, and reducer transitions.

## Addendum: connection layer placement (Steps 4–5)

- **Decision.** The Step 4 connection infrastructure required by the Step 5 screen lives in
  `:core:data`, not in a separate `:protocol:withrottle` adapter: the `DccClient` port,
  `WiThrottleClient` (incremental framing, greeting, version gate, heartbeat watchdog, capped
  backoff with jitter), `NsdManager` discovery, the DataStore saved-layout store, the scripted
  `DemoTransport`, and the app-scoped `ConnectionRepository`. `DccClient` currently speaks the
  WiThrottle vocabulary because there is one adapter; the interface is the seam for a future
  protocol-agnostic port.
- **Boundary.** `:feature:connection` depends on `:core:data` and `:core:model`, never on
  `:core:protocol`. It renders immutable `ConnectionUiState` and emits typed actions; sockets,
  protocol events, and exception text are below the ViewModel.
- **Local-network permission.** Verified against Android's current Local network permission
  documentation: with `targetSdk = 36`, LAN access is implicitly granted through `INTERNET`; apps
  must not declare or request `ACCESS_LOCAL_NETWORK`, and the Android 16 opt-in
  `NEARBY_WIFI_DEVICES` arrangement is deprecated. The manifest therefore declares only `INTERNET`
  and `ACCESS_NETWORK_STATE`. When the app moves to `targetSdk = 37`, `LocalNetworkAccess` and
  `LocalNetworkPermission` switch to `android.permission.ACCESS_LOCAL_NETWORK` and the manifest
  must declare it. The transport maps blocked sockets/NsdManager calls to
  `ConnectionError.LocalNetworkPermissionDenied`; no layer requests a permission or exposes system
  text.
- **Drive hand-off.** The nav graph now starts at `CONNECTION_ROUTE`. Once the repository reports
  `Connected`, the gate is removed from the back stack and Drive becomes the visible destination.
  Drive reads the same repository, so its link state and demo banner cannot disagree with Connect.
- **Revisit when.** A second protocol is funded, or Step 6 needs a session layer between the
  repository and the throttle engine. At that point the transport can move behind a protocol port
  without changing the connection screen.
