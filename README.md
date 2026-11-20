# OpenCab

OpenCab is a production-grade Android scaffold for a model-railroad cab controller. This
milestone establishes the build, module boundaries, theme, dependency injection, persistence
foundations, and an adaptive navigation shell. Networking, protocol framing, and throttle
semantics are intentionally not implemented yet.

## Status

The app builds, launches, and shows themed placeholder content for Drive, Roster, Settings, and
Connection. The event of connecting to a command station is represented only as scaffolded state.

## Requirements

- JDK 17 (the build uses a Java 17 toolchain)
- Android SDK Platform 36 and Build-Tools 36.0.0
- Android Studio with the matching SDK, or the Android command-line tools

The Gradle wrapper downloads Gradle 8.14.3 automatically.

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
run Gradle with `JAVA_HOME` pointing at a JDK 17 installation.

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
./gradlew :app:assembleDebugAndroidTest
```

Apply formatting fixes locally:

```bash
./gradlew spotlessApply
```

## Module map

| Module | Responsibility |
| --- | --- |
| `:app` | Application entry point, Hilt application, single Activity, adaptive navigation shell, app theme selection. |
| `:core:designsystem` | Material 3 theme, color schemes, typography, and shared Compose building blocks. |
| `:core:model` | Plain Kotlin domain types such as connection state and roster entries. |
| `:core:protocol` | Protocol module boundary and descriptor. No transport or command logic yet. |
| `:core:data` | Hilt modules, DataStore-backed settings, and the Room roster database scaffold. |
| `:feature:connection` | Placeholder Connection route and protocol status. |
| `:feature:throttle` | Drive screen with placeholder speed and throttle content. |
| `:feature:roster` | Placeholder roster empty state. |
| `:feature:settings` | Theme selection backed by DataStore, plus connection and about entries. |

Feature modules expose their own route constants and root composables. `:app` owns the `NavHost`
and the adaptive `NavigationSuiteScaffold`; feature modules do not depend on each other or on the
app module. Dependency direction flows from `:app` to features, then to `:core` modules. Features
do not depend on `:core:protocol`; the protocol boundary stays below the UI layer.

## Architecture notes

- **Single Activity + Compose.** `MainActivity` enables edge-to-edge drawing and hosts the Compose
  tree.
- **Adaptive navigation.** Material 3's adaptive navigation suite selects the appropriate compact or
  medium navigation bar, or a wide navigation rail, based on window size. The Connection route is
  outside the top-level destinations and hides the navigation suite while it is open.
- **Dependency injection.** Hilt is used across `:app`, `:core:data`, and `:feature:settings`.
  `:core:data` provides the settings repository, Room database, and DAO.
- **Persistence.** DataStore stores the selected app theme. Room contains a single scaffolded
  `saved_loco` table and DAO for the future roster, with schema export enabled.
- **Quality gates.** `./gradlew check` runs unit tests, lint, detekt, and Spotless. The GitHub
  Actions workflow runs the same checks and assembles the debug APK and instrumentation test APK.

## Intentionally not implemented

- DCC, LocoNet, XpressNet, or any other command-station transport
- Protocol framing, discovery, command encoding, and connection lifecycle
- Throttle acquisition, speed steps, functions, and emergency stop
- Roster CRUD beyond the empty database scaffold

## Continuous integration

`.github/workflows/ci.yml` runs on pushes and pull requests. It installs JDK 17 and Android SDK 36,
then runs `./gradlew check assembleDebug assembleDebugAndroidTest`.
