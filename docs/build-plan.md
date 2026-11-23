# OpenCab — Build Plan and Prompt Sequence

**Status:** Living planning document · **Created:** 2026-11-20
**Companions:** [`product-brief.md`](product-brief.md) · [`ux/ux-spec.md`](ux/ux-spec.md) · [`adr/0001-platform-and-stack.md`](adr/0001-platform-and-stack.md)

This document preserves the step-by-step build plan for OpenCab and the prompts used to
execute it. It is the durable record of *how* the app was planned, not a replacement for the
product brief, UX spec, or ADRs.

The prompts below are verbatim from the original plan. They were written before ADR-0001 was
accepted, so a few implementation details have since been refined. Where this document and an
accepted ADR disagree, **the ADR wins**; see [Known divergences](#known-divergences) before
executing a prompt.

---

## How to use this document

1. Read the product brief, UX spec, and relevant ADRs first.
2. Pick the next prompt from the [status table](#sequence-and-status).
3. Give an agent the [global preamble](#global-preamble) followed by exactly one prompt.
4. Run `./gradlew check`, review the diff, and commit the step.
5. Update the status table in the same commit as the work.
6. Tag meaningful checkpoints (`v0.1-scaffold`, `v0.2-protocol`, …).

A typical instruction to an agent:

```text
Read docs/build-plan.md. Follow the global preamble, then execute Prompt 3 only.
Do not start Prompt 4.
```

Do not run prompts concurrently. Each one assumes the previous step is merged and green.

---

## Original assumptions

These were the defaults used to draft the sequence:

- **Platform:** native Android, Kotlin, Jetpack Compose, Material 3 Expressive.
- **Protocol:** JMRI's WiThrottle over TCP first, because it is what EngineDriver speaks and it
  reaches Digitrax, NCE, ESU, and other command stations through JMRI. Z21 and LocoNet are
  later adapters, not v1.
- **Form factor:** phone-first, with phone landscape and tablets as first-class layouts.
- **v1 scope:** discover/connect, acquire, drive, functions, multi-throttle, linked consists,
  roster, settings, diagnostics, and a hardware-free demo mode.
- **v1 non-goals:** CV programming, turnout/layout control, routes, accounts, cloud sync,
  iOS.

The product brief is authoritative for scope. In particular, it narrows v1 to **one active
loco** and **read-only track power**, which is stricter than the later multi-throttle prompt.
Treat Prompt 9 as v1.1 work.

---

## Target architecture

The original module sketch:

```text
:app
:core:designsystem     theme, tokens, shared components
:core:model            domain types, no Android deps
:core:protocol         WiThrottle codec + session state machine, pure JVM
:core:data             transport, discovery, persistence, demo transport
:feature:connection
:feature:throttle
:feature:roster
:feature:settings
```

Data flows one way: Compose UI → ViewModel → throttle engine → `DccClient` → socket. Protocol
events return as `Flow`s. The engine owns coalescing, retries, and per-loco state so UI code
never touches the wire.

ADR-0001 refines this into `:protocol:withrottle`, `:core:session`, `:core:ui`,
`:feature:connect`, and `:feature:drive`. See the divergence note below before adding modules.

---

## Sequence and status

| # | Step | Status | Evidence / next action |
|---|------|--------|------------------------|
| 0 | Product and UX specification | **Done** | `docs/product-brief.md`, `docs/ux/ux-spec.md`, `docs/adr/0001-platform-and-stack.md` |
| 1 | Project scaffold | **Done** | Multi-module Gradle build, CI, adaptive nav shell, theme selection |
| 2 | Design system and app shell | **Done** | `CabTheme` + branded/high-contrast light/dark palettes, tabular type, shape/spacing/elevation/motion/haptic tokens, all eight shared components, adaptive suite (bottom bar compact / rail medium+expanded), predictive back, six-variant `@CabPreview` matrix, contrast + semantics tests, and `docs/design-system.md`. No screenshot harness is configured; the pinned-version gap is documented. |
| 3 | WiThrottle protocol core | **Done** | `:core:protocol` contains the incremental line codec, typed commands/events, and pure `SessionReducer`; `docs/protocol.md` records exact mappings, version gates, the supported/unsupported matrix, and known protocol quirks. Golden, captured-transcript, chunk-boundary, round-trip, malformed-input, duplicate/unknown, and reducer-transition tests are in `core/protocol/src/test`. |
| 4 | Transport, discovery, persistence | **Done** | `:core:data` has the `DccClient` port, `WiThrottleClient` (incremental framing, handshake/version gate, heartbeat watchdog, capped backoff with jitter, typed errors), a validated DataStore saved-layout store, cancelable `NsdManager` discovery, and a scripted `DemoTransport`. Fake-TCP-server tests cover dropped mid-frame links, heartbeat timeout, reconnect, concurrent close, invalid handshake, version mismatch, cancellation, and command round trips. See `docs/connection.md`. |
| 5 | Connection UX | **Done** | Connect is the navigation gate and shows discovered/saved/manual/demo sections, explicit scanning/timeout/empty/rescan states, local-network rationale with a Settings deep link, server cards with connect/cancel/retry/edit, typed plain-language failures, a validated manual sheet (default 12090), a labelled demo session, immutable ViewModel state, an obvious Drive hand-off, six-state previews, and Compose UI tests. See `docs/connection.md`. |
| 6 | Throttle state engine | **Not started** | — |
| 7 | Throttle UI | **Scaffold only** | Drive screen styled with `SpeedDisplay`, `StatusPill`, `SectionCard`, and `PrimaryActionButton`; no throttle state or gestures |
| 8 | Roster and acquisition | **Scaffold only** | Room entity/DAO and a roster empty state styled with the design system |
| 9 | Multi-throttle and linked consists | **Not started** | Planned as v1.1 by the product brief |
| 10 | Settings, diagnostics, polish | **Not started** | Theme setting exists; the rest is open |
| 11 | Hardening and release | **Not started** | — |
| 12 | Stretch backlog | **Backlog** | Z21, LocoNet, accessories |

**MVP cut line:** Prompt 8. **Shippable v1.0:** Prompt 11.

---

## Global preamble

Prepend this to every prompt:

```text
You are working on OpenCab, a native Android DCC throttle app, in this repository.

Before starting: read README.md, everything under docs/, and the ADRs. Implement
only the step below. Do not start later steps or "helpfully" add adjacent features.

Rules:
- Keep :core:protocol and :core:model free of Android dependencies.
- Prefer existing patterns and dependencies over new ones. Justify any new dependency.
- Add tests for behavior and edge cases, not implementation details.
- Run ./gradlew check before declaring done. Fix failures rather than disabling checks.
- Update docs and ADRs when a decision changes.
- No placeholder UI in shipped paths. Empty, loading, error, and offline states are part of the feature.
- If something blocks you, ask one focused question. Otherwise choose the simplest
  reversible option and record it.

Finish with: files changed, decisions made, known risks, and the exact next step.
```

---

## Prompts

### Prompt 0 — Product and UX specification

```text
Step 0: Product definition and UX specification.

Act as product designer and Android architect. Produce:

1. docs/product-brief.md
   - Target users: solo home operator, club night with shared layout, exhibition operator.
   - Jobs-to-be-done and the top 5 tasks, ranked.
   - v1 scope and explicit non-goals (no CV programming, no turnout/layout control, no accounts).
   - Success criteria: time from launch to moving a train, reconnect success, crash-free sessions,
     one-handed operation.

2. docs/ux/ux-spec.md
   - Screen inventory and navigation map. Drive is the home destination; Connection is a
     full-screen gate; Roster and Settings are secondary.
   - Primary flow: discover server → connect → pick loco → acquire → drive → release.
   - Edge states: no server found, wrong port, permission denied, dropped connection mid-run,
     loco already acquired elsewhere, track power off, server version mismatch.
   - Throttle interaction principles: one-handed reach, no accidental speed jumps,
     immediate stop, glanceable state, forgiving reconnect.
   - Adaptive layout rules: compact phone, phone landscape, tablet.
   - Accessibility principles: 48dp targets, TalkBack semantics, 200% font, reduced motion,
     no color-only status.
   - Haptics and motion principles.

3. docs/adr/0001-platform-and-stack.md
   - Kotlin + Compose + Material 3 Expressive, single Activity, module boundaries.
   - WiThrottle first, other protocols behind the same abstraction.
   - Consequences and rejected alternatives (Flutter, web wrapper, protocol-first UI).

Ground the design in current Material 3 Expressive guidance. Do not write app code.
Ask at most five blocking questions; otherwise state assumptions explicitly.
```

### Prompt 1 — Scaffold the project

```text
Step 1: Production-grade Android scaffold.

Create a multi-module Android project named OpenCab, applicationId dev.opencab.

Requirements:
- Kotlin 2.x, Jetpack Compose, Gradle Kotlin DSL, version catalog, JDK 17 toolchain.
- minSdk 26, targetSdk 36, compileSdk 36. Edge-to-edge by default.
- Modules: :app, :core:designsystem, :core:model, :core:protocol, :core:data,
  :feature:connection, :feature:throttle, :feature:roster, :feature:settings.
- Hilt, kotlinx-coroutines, DataStore, Room, JUnit, Turbine, Compose UI test.
- Static analysis (ktlint/spotless + detekt or equivalent), lint configured, and
  ./gradlew check running unit tests + lint.
- GitHub Actions workflow building and testing on push/PR.
- Single Activity with Compose, a placeholder adaptive navigation shell with Drive,
  Roster, Settings, and a placeholder Connection route.
- README with setup, run, test, and module map.

Pin versions that actually resolve with the installed toolchain; do not invent versions.
Do not implement networking, protocol, or throttle logic yet. The app should build,
launch, and show themed placeholder content.
```

### Prompt 2 — Design system and app shell

```text
Step 2: Material 3 Expressive design system and adaptive shell.

Build :core:designsystem and replace placeholder styling.

Deliver:
- CabTheme: dynamic color opt-in plus a branded fallback scheme, light and dark.
  Use the expressive theme/motion APIs available in the pinned Compose Material3 version;
  if an API is unavailable, compose it from primitives and document the gap.
- Tokens: color roles, typography with tabular numerals for speed/address readouts,
  shape scale, spacing, tonal elevation, motion specs (springs), haptic tokens.
- Visual direction: precision instrument panel, not skeuomorphic wood-and-brass.
  High contrast, large numerals, generous touch surfaces, restrained color.
- Components: StatusPill (connected/reconnecting/offline), SpeedDisplay (visual only),
  FunctionKey (visual states: off, active, momentary, disabled), SectionCard,
  EmptyState, InlineError, ConfirmSheet, PrimaryActionButton.
- Adaptive navigation with NavigationSuiteScaffold: bottom bar on compact,
  rail on medium/expanded. Correct insets and predictive back.
- Previews for light/dark, font scale 1.0 and 2.0, phone portrait, phone landscape,
  and tablet. Add screenshot tests if the project supports them.

No feature logic. Components must be reusable and have semantics for TalkBack.
```

### Prompt 3 — WiThrottle protocol core

```text
Step 3: Pure-Kotlin WiThrottle protocol layer.

Implement :core:protocol with no Android imports.

Deliver:
- Incremental, newline-framed codec that tolerates partial frames, multiple frames per
  read, and unknown messages. Unknown input becomes an Unknown event and is logged, never fatal.
- Typed commands/events for: version handshake, heartbeat, track power, acquire/release,
  speed and direction, emergency stop, functions and function labels, and the consist
  messages needed later. Do not invent message syntax; follow the official WiThrottle
  protocol documentation and record exact mappings in docs/protocol.md.
- A pure SessionReducer state machine: consumes decoded events and emits outbound commands.
  It owns handshake, heartbeat scheduling policy, power state, and per-loco acquisition state.
  No sockets, threads, clocks, or coroutines with real time inside.
- Supported/unsupported matrix in docs/protocol.md, including version differences and
  behavior for unsupported messages.
- Tests: golden messages from the protocol docs, captured transcripts, chunk-boundary
  splitting, round-trip encoding, malformed input, duplicate/unknown messages, and
  state-machine transitions. Aim for high coverage on the codec and reducer.

This layer must be testable with plain JVM tests. No networking yet.
```

### Prompt 4 — Transport, discovery, and persistence

```text
Step 4: Connection infrastructure.

Build :core:data.

Deliver:
- DccClient interface: connect, outbound command channel, inbound event flow, close.
- WiThrottleClient implemented with coroutine sockets, read/write loops, timeouts,
  heartbeat watchdog, and a connection state machine:
  Disconnected → Connecting → Connected → Reconnecting → Failed.
- Reconnect with exponential backoff and jitter. Cancellation must close sockets cleanly
  and leak no coroutines.
- ServerDiscovery interface with an NsdManager implementation for `_withrottle._tcp`,
  plus a fake for tests. Scanning must be cancelable and have a timeout.
- Saved servers in DataStore: name, host, port, last connected. No credentials
  (WiThrottle has none). Validate host/port; default port 12090.
- Typed error taxonomy mapped to user-safe messages, including network unreachable,
  timeout, protocol mismatch, local-network permission denied, and server closed.
- DemoTransport: a scripted fake DCC server that supports acquire, speed, functions,
  disconnect, and reconnect. It must make the whole app usable without hardware.
- Tests with a fake TCP server: dropped connection mid-frame, heartbeat timeout,
  reconnect, concurrent close, invalid handshake.

Handle the Android 16+ local-network access permission at the boundary (surface a typed
error; do not request permissions here). Verify the exact permission name against the
target SDK docs. No UI in this step.
```

### Prompt 5 — Connection UX

```text
Step 5: Connection screen and flow.

Build :feature:connection and wire it into navigation.

Deliver:
- Connect route with sections: discovered servers, saved servers, manual entry, demo mode.
- Scan on entry with an explicit scanning state, timeout, empty state, and rescan action.
- Permission rationale for local network access, with a Settings deep link when denied.
- Server cards showing name, host:port, last connected, and status. Connect in progress,
  cancel, retry, and edit server.
- Manual entry sheet: host, port, inline validation, correct keyboard types, default 12090.
- Plain-language error copy with a recovery action, never raw exception text.
- Demo mode entry that starts a DemoTransport session and clearly labels the UI as demo.
- ViewModel + immutable UI state; no sockets or protocol code in composables.
- UI tests and previews for: scanning, empty, permission denied, connecting, failed,
  and connected states.

Keep the transition into Drive obvious. Do not build the throttle yet.
```

### Prompt 6 — Throttle state engine

```text
Step 6: Multi-slot throttle engine.

Build the domain engine that sits between UI and DccClient. No Compose code.

Deliver:
- ThrottleController owning multiple slots, each with address, address type, name,
  speed steps (14/28/128), normalized speed, direction, function map, acquisition state,
  and connection health.
- Immutable ThrottleUiState exposed as StateFlow; intents for acquire, release, setSpeed,
  nudge, setDirection, toggleFunction, emergencyStop, and link/unlink.
- Speed writes coalesced to at most ~10 Hz with a guaranteed trailing value; stop and
  emergency stop bypass coalescing and take priority.
- Correct handling of acquire conflicts ("in use"), power-off, disconnect, and reconnect.
  On disconnect: keep last known state, mark it stale, disable input, and re-acquire
  automatically on reconnect if the slot is still desired.
- Per-loco emergency stop is separate from global track power off.
- Function support 0–28 with labels, momentary/latching behavior, and disabled states.
- Virtual-time tests for coalescing, ordering, reacquire, conflicts, partial failures,
  and cancellation. Deterministic tests only.

Expose a clean API for the UI. Do not implement multi-throttle UI or consists yet.
```

### Prompt 7 — The throttle screen

```text
Step 7: The hero throttle screen.

Build :feature:throttle. This screen is the product; optimize it for one-handed,
no-look operation.

Layout:
- Portrait: loco header (name, address, connection pill), dominant vertical throttle
  lever, direction control, large STOP within thumb reach, expandable function grid.
- Landscape/tablet: lever and functions side by side, no scrolling needed to drive.
- Speed display: step count always; optional estimated scale speed when calibrated.
  Mark estimates clearly.

Interaction:
- Grab-the-handle absolute drag; dragging the background does nothing, preventing
  accidental jumps. Add +/- nudge buttons for single-step control and a zero/stop action.
- Haptic tick per step, stronger detent at zero and maximum, medium confirmation on
  direction change. Respect system haptics and a future intensity setting.
- Functions: labeled keys, clear active state, press-and-hold for momentary functions.
- Connection-lost banner that disables controls without covering the layout.
- Release loco requires confirmation while moving.

Quality:
- 60fps drag: local lever state, graphicsLayer transforms, no whole-screen recomposition.
- TalkBack: adjustable semantics with setProgress, custom actions for stop/forward/reverse,
  announcements at coarse intervals rather than every tick.
- 200% font support, contrast, 48dp targets, reduced motion.
- Preview and screenshot states: connected, reconnecting, stale, in-use error, max speed,
  active functions, long labels.

No roster or multi-throttle UI yet.
```

### Prompt 8 — Roster and acquisition

```text
Step 8: Loco roster and acquisition flow.

Build :feature:roster and the loco picker used by Drive.

Deliver:
- Room entity for locos: name, address, address type (short/long), function labels,
  favorite flag, color/icon, last used timestamp. Include a migration test.
- Address validation: short 1–127; long 1–10239, flagging the ambiguous 1–127 overlap
  and defaulting to short. Prevent duplicate acquisition in the app and surface server
  "in use" errors clearly.
- Picker bottom sheet: search by name or address, recents, favorites, and "add address".
  Empty state leads directly to adding the first loco.
- Loco detail/edit screen: rename, favorite, color/icon, function labels. Changing an
  address creates a new roster entry rather than silently mutating history.
- Use server-provided function labels when available; local labels override them.
- Optional JMRI roster import behind a feature flag only if the protocol supports it
  cleanly. It must not block the rest of the step.
- Tests for validation, persistence, duplicate prevention, and picker filtering.

After this step the app must be usable end-to-end on a real layout.
```

### Prompt 9 — Multi-throttle and linked consists

```text
Step 9: Multiple throttles and linked consists.

Deliver:
- Up to four open throttle slots. Compact phone: horizontal pager with clear page indicator.
  Phone landscape: two side by side. Tablet: 2x2 grid. Active slot is visually obvious.
- Add throttle via FAB and picker; release/close via overflow with confirmation.
- App-managed linking: designate a leader, link members, mirror speed, direction, and
  selected functions. Link/unlink per slot, visible grouping, and "stop all" behavior.
  Emergency stop on any linked member stops the whole group.
- Server-side consisting only if the protocol subset is verified; otherwise app-side
  linking is the supported path, documented as such.
- Handle partial failures: member disconnects, member acquisition fails, leader released.
- Restore open slots and linkage on reconnect when possible.
- Tests for fan-out, ordering, partial failure, and unlink behavior.
- Performance check with four active throttles: no jank, bounded recomposition.

Keep the single-throttle experience unchanged when only one slot is open.
```

### Prompt 10 — Settings, diagnostics, and polish

```text
Step 10: Settings, diagnostics, accessibility, and localization polish.

Deliver:
- DataStore-backed settings: theme (system/light/dark), dynamic color, haptics intensity,
  keep screen awake while driving, default speed steps, scale and units for estimated speed,
  reconnect behavior, demo mode.
- Diagnostics screen: connection state, protocol log ring buffer (last ~500 lines),
  copy/share, clear, and exported session info. Redact nothing sensitive because there are
  no secrets, but do not log message payloads unnecessarily.
- Accessibility sweep: TalkBack labels, focus order, custom actions, 200% font, contrast,
  reduced motion, no color-only status.
- Localization readiness: all strings in resources, plurals, RTL-safe layouts.
- State sweep: every screen has intentional loading, empty, error, offline, and stale states.
- Predictive back consistently returns to a safe screen; never leave a moving loco
  without a visible throttle.
- About screen with licenses and a clear statement that the app talks only to the local
  network and collects no analytics by default.
```

### Prompt 11 — Hardening and release

```text
Step 11: Production hardening and release readiness.

Deliver:
- Test suite review: protocol golden tests, engine virtual-time tests, fake-server
  integration tests, Compose UI tests, screenshot tests. Fill meaningful gaps only.
- Baseline profile and macrobenchmarks for cold start and throttle drag jank.
  Set explicit budgets and fail CI on regressions.
- R8/minification, resource shrinking, signing config driven by environment variables,
  versionCode/versionName strategy, and a signed AAB build in CI.
- CI matrix for unit tests, lint, and assemble. Cache Gradle.
- Release checklist: permissions rationale, data safety form (no data collected),
  privacy policy, Play listing copy, screenshots, and a hardware/JMRI compatibility matrix.
- Verify behavior against JMRI with a simulated command station and, if available,
  at least one physical system. Document known protocol quirks.
- Final pass on crash paths: no unhandled exceptions on socket close, rapid connect/disconnect,
  rotation mid-command, or process death.

Done when ./gradlew check is green, a signed release build exists, and performance budgets
are measured rather than assumed.
```

### Prompt 12 — Stretch backlog (after v1)

```text
Step 12 (post-v1): Protocol and feature expansion.

Only after v1 is stable:
- Add Z21 (UDP) behind the existing DccClient abstraction, proving the abstraction.
- Consider LocoNet-over-TCP if there is demand.
- Turnout/accessory control and routes, designed as a separate destination, not crammed
  into Drive.
- Automation/macros, home-screen widgets, Wear OS companion.
- Do not break WiThrottle behavior or the throttle-first UX while adding these.
```

---

## Known divergences

Resolve these before starting Prompt 3. They are recorded here rather than silently patched
into the prompts, because the prompts are the planning record.

| Area | Original plan | Current ADR / code | Recommendation |
|---|---|---|---|
| DI | Hilt | ADR-0001 excludes Hilt in favour of a manual `AppContainer`; the scaffold already uses Hilt | Pick one. Updating ADR-0001 to accept Hilt is cheaper than undoing the scaffold |
| Persistence | Room for the roster | ADR-0001 excludes Room; the scaffold already has a Room database | Either accept Room for the roster or remove the scaffold before Prompt 8 |
| Module names | `:feature:connection`, `:feature:throttle` | ADR-0001 proposes `:feature:connect`, `:feature:drive`, plus `:core:session`, `:core:ui`, `:protocol:withrottle` | Adopt ADR names now or amend the ADR; do not grow both |
| Protocol split | Codec in `:core:protocol`, transport in `:core:data` | ADR-0001 puts ports in `:core:protocol` and the WiThrottle adapter in `:protocol:withrottle`; session state in `:core:session` | Resolved for Step 3: the codec and pure reducer live in `:core:protocol` per the explicit prompt, and the ADR-0001 addendum records the deferred adapter/session split. Revisit when a second protocol is funded. |
| Loco count | Prompt 9 adds multi-throttle | Product brief v1 allows one active loco; multi-throttle is v1.1 | Keep Prompt 9 out of v1 |

The scaffold commit (`2eb3a82`) and ADR-0001 currently disagree. Settle that in one commit
before protocol work so the codebase does not carry two architectures.

---

## Execution notes

- **Test without hardware from day one.** Run JMRI with a simulated command station and its
  WiThrottle server. Prompt 4's `DemoTransport` means UI work never waits for a layout.
- **Commit after every prompt** and tag checkpoints (`v0.1-scaffold`, `v0.2-protocol`, …).
  Each prompt is sized to be reviewable on its own.
- **Biggest risks:** protocol quirks across JMRI versions and command stations; socket
  lifecycle bugs; accidental throttle input; scope creep into CV programming. The plan
  mitigates these with a tolerant codec, a pure reducer, explicit UX rules, and hard
  non-goals.
- **Verify library specifics at implementation time.** Material 3 Expressive APIs and the
  Android 16 local-network permission are moving targets; pin versions and check the
  official docs rather than trusting a plan written months earlier.

---

## Maintenance

- Update the status table whenever a step lands.
- If a prompt changes materially during execution, record the change here rather than
  rewriting history.
- If an ADR supersedes part of the plan, add a row to [Known divergences](#known-divergences).
- Keep the product brief as the source of truth for scope; this document is the source of
  truth for sequence.
