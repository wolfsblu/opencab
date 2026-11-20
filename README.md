# OpenCab

OpenCab is a production-grade Android scaffold for a model-railroad cab controller. This
milestone establishes the build, module boundaries, theme, dependency injection, persistence
foundations, an adaptive navigation shell, and a pure-Kotlin WiThrottle protocol core. Networking,
discovery, and throttle UI are intentionally not implemented yet.

## Status

The app builds, launches, and shows the OpenCab design system on every placeholder screen: a
branded Material 3 theme with opt-in dynamic colour, tabular numeric readouts, shared
status/control components, and an adaptive navigation shell. The event of connecting to a
command station is represented only as scaffolded state.

`:core:protocol` now contains the WiThrottle line codec, typed commands and events, and a pure
session reducer. It has no Android, socket, thread, clock, or coroutine dependency and is covered
by plain JVM golden, transcript, chunk-boundary, round-trip, malformed-input, and state-machine
tests. The protocol reference and supported/unsupported matrix are in
[`docs/protocol.md`](docs/protocol.md).

The design system reference — tokens, components, semantics, preview matrix, and the gaps in the
pinned Material 3 version — lives in [`docs/design-system.md`](docs/design-system.md).

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
| `:core:data` | Hilt modules, DataStore-backed settings, and the Room roster database scaffold. |
| `:feature:connection` | Placeholder Connection route and protocol status. |
| `:feature:throttle` | Drive screen composed from `SpeedDisplay`, `StatusPill`, `SectionCard`, and `PrimaryActionButton`; no throttle logic yet. |
| `:feature:roster` | Roster empty state composed from the design system; no roster logic yet. |
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
  bottom bar at compact width and a navigation rail at medium/expanded width. The Connection route
  is outside the top-level destinations and hides the suite while it is open. The suite consumes
  the navigation-bar/rail inset; `CabScaffold` consumes the status-bar inset.
- **Predictive back.** `MainActivity` enables the system back callback. The app wraps its
  `NavHost` in `CabPredictiveBackContainer`, which renders predictive-back progress; Material
  modal sheets provide their own predictive-back behaviour.
- **Dependency injection.** Hilt is used across `:app`, `:core:data`, and `:feature:settings`.
  `:core:data` provides the settings repository, Room database, and DAO.
- **Protocol core.** `:core:protocol` owns the newline-framed WiThrottle codec, typed commands and
  events, and the pure `SessionReducer`. The reducer owns handshake, heartbeat scheduling policy,
  track-power state, and per-loco acquisition state; all time is supplied by the caller. Unknown
  or malformed input becomes a logged `Unknown` event and never terminates a session. Exact wire
  mappings, version gates, and the supported/unsupported matrix are in [`docs/protocol.md`](docs/protocol.md).
- **Persistence.** DataStore stores the selected app theme. Room contains a single scaffolded
  `saved_loco` table and DAO for the future roster, with schema export enabled.
- **Quality gates.** `./gradlew check` runs unit tests, lint, detekt, and Spotless. The GitHub
  Actions workflow runs the same checks and assembles the debug APK and instrumentation test APK.

## Intentionally not implemented

- DCC, LocoNet, XpressNet, or any other command-station transport
- Sockets, server discovery, connection lifecycle, reconnect, and heartbeat timers
- Throttle UI state, speed coalescing, and gesture handling
- Roster CRUD beyond the empty database scaffold

## Continuous integration

`.github/workflows/ci.yml` runs on pushes and pull requests. It installs JDK 21 and Android SDK 36,
then runs `./gradlew check assembleDebug assembleDebugAndroidTest`.
