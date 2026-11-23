# OpenCab

OpenCab is a production-grade Android scaffold for a model-railroad cab controller. This
milestone establishes the build, module boundaries, theme, dependency injection, persistence
foundations, an adaptive navigation shell, a pure-Kotlin WiThrottle protocol core, the connection
gate (mDNS discovery, saved layouts, manual entry, and a live socket handshake), the multi-slot
throttle domain engine, the hero drive screen, and a persistent locomotive roster with a
Drive picker.

## Status

The app builds, launches into the **Connect** gate, and finds WiThrottle servers. The design system
provides a branded Material 3 theme with opt-in dynamic colour, tabular numeric readouts, shared
status/control components, and an adaptive navigation shell. Drive is now the hero throttle: one
instrument bay with a recessed readout window, a full-height grab-the-handle lever with detent
ticks, a Direction + Stop deck integrated into the bay, press-and-hold functions,
and inline link-loss states. Compact portrait puts Throttle and Functions on tabs over a full-bleed
bay with the Direction + Stop deck pinned and inset from the display corners; landscape puts the
deck under the readout and lets the lever span the bay height; tablets use a capped, centred
console. The function grid stays visually quieter than the throttle.

Roster is live. Locomotives are stored in Room with name, typed DCC address, local and server
function labels, favourite, colour/icon, and last-used time. The Drive picker searches by name or
address, groups favourites and recents, and adds an address in place; the roster editor handles
rename, favourite, appearance, and labels. Changing an address appends a new entry instead of
rewriting history. Duplicate addresses are rejected at the database and repository layers, and
re-selecting the active locomotive never creates a second throttle slot. See
[`docs/roster.md`](docs/roster.md).

`:core:data` contains the `ThrottleController` engine: multiple desired slots, immutable
`ThrottleUiState`, acquisition/steal/release, 10 Hz speed coalescing with a guaranteed trailing
value, priority stop/E-stop, disconnect/stale/re-acquire, per-loco E-stop, and functions 0–28.
`ThrottleSession` attaches the engine to the live connection and exposes the single-slot action
surface used by the drive screen. Its API and deterministic virtual-time tests are documented in
[`docs/throttle-engine.md`](docs/throttle-engine.md); the screen is documented in
[`docs/drive-screen.md`](docs/drive-screen.md).

`:core:data` also owns the roster repository: a typed-address Room entity with a v1→v2 migration,
duplicate-address reporting, label encoding, last-used tracking, and server-roster import. Roster
and picker view models see only `RosterRepository` and the narrow `ThrottleUiSource` port; the
protocol never crosses into the feature UI.

`:core:protocol` now contains the WiThrottle line codec, typed commands and events, and a pure
session reducer. It has no Android, socket, thread, clock, or coroutine dependency and is covered
by plain JVM golden, transcript, chunk-boundary, round-trip, malformed-input, and state-machine
tests. The protocol reference and supported/unsupported matrix are in
[`docs/protocol.md`](docs/protocol.md).

The design system reference — tokens, components, semantics, preview matrix, and the gaps in the
pinned Material 3 version — lives in [`docs/design-system.md`](docs/design-system.md).

The connection flow reference — the `DccClient` transport, discovery, permission handling,
saved layouts, typed errors, reconnect policy, and state mapping — lives in
[`docs/connection.md`](docs/connection.md).

## Planning

The step-by-step build plan, prompt sequence, and implementation status live in
[`docs/build-plan.md`](docs/build-plan.md). Start there before beginning a new milestone.

## Requirements

- JDK 21 (latest 21.0.x patch; used to run Gradle and as the Kotlin/Java compile toolchain).
  Run Gradle itself on JDK 21: detekt's embedded Kotlin parser cannot read a newer JDK's
  version string, so `./gradlew check` fails on JDK 25 even though compilation succeeds.
- Android source and Kotlin target remain at Java 17 for runtime compatibility
- Android SDK Platform 36 and Build-Tools 36.0.0
- Android Studio with the matching SDK, or the Android command-line tools

The Gradle wrapper downloads Gradle 9.6.1 automatically.

## Setup

1. Clone the repository.
2. Point Gradle at the Android SDK. Either export `ANDROID_HOME`, or create an untracked
   `local.properties` file:

   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```

3. Verify the toolchain:

   ```bash
   ./gradlew --version
   ```

The build defaults to the Java toolchain configured by Gradle. If multiple JDKs are installed,
run Gradle with `JAVA_HOME` pointing at a JDK 21 installation.

## Run

Build the debug APK:

```bash
./gradlew :app:assembleDebug
```

Install it on a connected device or emulator:

```bash
./gradlew :app:installDebug
```

Or open the project in Android Studio and run the `app` configuration.

## Test and checks

Run unit tests, Android Lint, detekt, and Spotless in one command:

```bash
./gradlew check
```

Run the Compose instrumentation tests on a connected device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Compile instrumentation tests without a device:

```bash
./gradlew :app:assembleDebugAndroidTest :core:designsystem:assembleDebugAndroidTest
```

Run the design-system unit tests (WCAG contrast and motion tokens):

```bash
./gradlew :core:designsystem:testDebugUnitTest
```

Run the protocol codec and reducer tests:

```bash
./gradlew :core:protocol:test
```

Run the connection infrastructure and connection-screen unit tests:

```bash
./gradlew :core:data:testDebugUnitTest :feature:connection:testDebugUnitTest
```

Run the throttle-engine virtual-time tests:

```bash
./gradlew :core:data:testDebugUnitTest --tests 'dev.opencab.core.data.throttle.*'
```

Run the drive-screen mapping tests, and the gesture/state Compose tests on a device:

```bash
./gradlew :feature:throttle:testDebugUnitTest
./gradlew :feature:throttle:connectedDebugAndroidTest
```

Run roster validation, persistence, migration, duplicate, picker, and editor tests:

```bash
./gradlew :core:model:test :core:data:testDebugUnitTest :feature:roster:testDebugUnitTest
```

Every shared component and feature screen carries a `@CabPreview` matrix: light/dark, 100 %/200 %
font, phone portrait, phone landscape, and tablet. There is no screenshot-test harness configured,
so previews are the visual review surface; the reasoning is recorded in
[`docs/design-system.md`](docs/design-system.md#6-tests).

Apply formatting fixes locally:

```bash
./gradlew spotlessApply
```

## Module map

| Module | Responsibility |
| --- | --- |
| `:app` | Application entry point, Hilt application, single Activity, navigation graph, and app theme selection. |
| `:core:designsystem` | `CabTheme` and design tokens (colour, tabular type, shape, spacing, elevation, motion, haptics), shared components, adaptive navigation shell, predictive-back container, and the preview matrix. |
| `:core:model` | Plain Kotlin domain types such as connection state and roster entries. |
| `:core:protocol` | Pure JVM WiThrottle wire codec, typed commands/events, and the deterministic session reducer. No transport or Android imports. |
| `:core:data` | Hilt modules, DataStore-backed settings and saved layouts, the Room roster repository and v1→v2 migration, `DccClient` transport port, `WiThrottleClient` (framing, handshake, heartbeat watchdog, reconnect), `NsdManager` discovery, and the multi-slot `ThrottleController` engine. |
| `:feature:connection` | Connect gate: discovery, permission rationale, saved layouts, manual entry, typed failures, and the Drive hand-off. |
| `:feature:throttle` | Hero drive screen: loco header, one instrument bay with a recessed step/estimate readout, a full-height grab-the-handle lever, an integrated Direction + Stop deck, portrait Throttle/Functions tabs with the deck pinned, press-and-hold functions, inline link-loss/issue states, and the `DriveViewModel` that maps `ThrottleSession` state and local roster function labels. |
| `:feature:roster` | Room-backed roster list, searchable Drive picker with favourites/recents/add-address, and the loco detail/editor with typed address validation, favourite, colour/icon, and function labels. |
| `:feature:settings` | Theme selection backed by DataStore, plus connection and about entries. |

Feature modules expose their own route constants and root composables. `:app` owns the `NavHost`
and wires the design system's adaptive navigation suite; feature modules do not depend on each
other or on the app module. Dependency direction flows from `:app` to features, then to `:core`
modules. Features do not depend on `:core:protocol`; the protocol boundary stays below the UI
layer.

## Architecture notes

- **AGP 9 built-in Kotlin.** Android modules use AGP 9.5's built-in Kotlin support. `:core:model`
  and `:core:protocol` remain pure Kotlin/JVM modules.
- **JDK 21 toolchain, Java 17 target.** Gradle and the compiler toolchain run on JDK 21, while
  Android `sourceCompatibility`/`targetCompatibility` and Kotlin `jvmTarget` stay at 17.
- **Single Activity + Compose.** `MainActivity` enables edge-to-edge drawing and hosts the Compose
  tree.
- **Design system.** `:core:designsystem` owns the `CabTheme`, branded light/dark and
  high-contrast palettes, fixed semantic status colours, tabular numeric type roles, spacing and
  touch tokens, tonal elevation, expressive/standard spring tokens, and the haptic vocabulary.
  Dynamic colour is opt-in and falls back to the branded palette when contrast validation fails.
  Material3 1.4.0 keeps `MaterialExpressiveTheme` and `MotionScheme` internal; OpenCab composes
  them from public primitives and documents the gap.
- **Adaptive navigation.** `CabNavigationSuite` wraps `NavigationSuiteScaffold` and always uses a
  bottom bar at compact width and a navigation rail at medium/expanded width. The graph starts at
  `CONNECTION_ROUTE`, which is outside the top-level destinations and hides the suite while it is
  open. A successful connect removes the gate from the back stack and makes Drive visible. The suite
  consumes the navigation-bar/rail inset; `CabScaffold` consumes the status-bar inset.
- **Predictive back.** `MainActivity` enables the system back callback. The app wraps its
  `NavHost` in `CabPredictiveBackContainer`, which renders predictive-back progress; Material
  modal sheets provide their own predictive-back behaviour.
- **Dependency injection.** Hilt is used across `:app`, `:core:data`, and the feature modules.
  `:core:data` provides the settings repository, saved-layout store, mDNS discovery, the app-scoped
  connection repository, and the Room database/DAO.
- **Protocol core.** `:core:protocol` owns the newline-framed WiThrottle codec, typed commands and
  events, and the pure `SessionReducer`. The reducer owns handshake, heartbeat scheduling policy,
  track-power state, and per-loco acquisition state; all time is supplied by the caller. Unknown
  or malformed input becomes a logged `Unknown` event and never terminates a session. Exact wire
  mappings, version gates, and the supported/unsupported matrix are in [`docs/protocol.md`](docs/protocol.md).
- **Connection boundary.** `:core:data` owns the `DccClient` port, the live socket, the client
  greeting, the version gate, link-level heartbeats and watchdog, capped reconnect backoff with
  jitter, discovery, and saved layouts. `AndroidNetworkBinder` binds
  throttle sockets to the active Wi-Fi/Ethernet network so layout access points without internet
  are reachable while cellular remains Android's default route. `:feature:connection` sees only
  `ConnectionUiState` and typed `ConnectionError` values; it has no socket or protocol imports.
  Exact state mapping, fake-server test coverage, and permission notes are in
  [`docs/connection.md`](docs/connection.md).
- **Throttle engine.** `:core:data` owns the `ThrottleController` domain engine. It serializes UI
  intents and client events through one actor, reuses `SessionReducer` for acquisition/function
  state, coalesces speed writes to 10 Hz with a guaranteed trailing value, gives stop and per-loco
  E-stop priority, and keeps stale slots visible but disabled across disconnect. Reconnect
  automatically re-acquires desired slots and commands speed zero before control returns.
  `ThrottleSession` attaches the engine to `ConnectionRepository.activeClient`, exposes single-slot
  actions without leaking protocol types to the UI, and therefore survives a socket reconnect. The
  UI contract, lifecycle, and virtual-time tests are in
  [`docs/throttle-engine.md`](docs/throttle-engine.md). The screen's interaction, accessibility, and
  performance contract is in [`docs/drive-screen.md`](docs/drive-screen.md).
- **Local-network permission.** With `targetSdk = 36`, LAN access is implicit through `INTERNET`;
  no local-network permission is declared or requested. Targeting SDK 37+ will require
  `android.permission.ACCESS_LOCAL_NETWORK`. Blocked traffic still maps to a typed
  `ConnectionError.LocalNetworkPermissionDenied`.
- **Persistence.** DataStore stores the selected app theme and saved layouts (name, host, port, last
  connected). Room contains the `roster_loco` table with a unique typed-address index, local and
  server function labels, favourite, colour/icon keys, and last-used time. Schema export is
  enabled and the v1→v2 migration is covered by a Robolectric test.
- **Quality gates.** `./gradlew check` runs unit tests, lint, detekt, and Spotless. The GitHub
  Actions workflow runs the same checks and assembles the debug APK and instrumentation test APK.

## Intentionally not implemented

- DCC, LocoNet, XpressNet, or any other command-station transport
- Multi-throttle UI, app-side linking, and linked consists
- Calibration, speed-cap, hold-to-run, and haptic-intensity settings (the UI accepts the values)
- Process-death restoration of open throttle slots
- Roster delete and bulk editing; address changes deliberately append rather than replace
- Diagnostics log export and the remaining settings that tune reconnect behaviour

## Continuous integration

`.github/workflows/ci.yml` runs on pushes and pull requests. It installs JDK 21 and Android SDK 36,
then runs `./gradlew check assembleDebug assembleDebugAndroidTest`.
