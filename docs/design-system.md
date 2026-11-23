# OpenCab design system

`:core:designsystem` owns the Material 3 theme, design tokens, shared components, and the
adaptive navigation shell. Feature modules consume it; they do not re-style Material components
or invent local colour, type, spacing, or motion values.

The visual direction is a **precision instrument panel**. Graphite and cool paper surfaces, one
instrument-blue accent, tabular numerals, generous touch targets, and restrained signal colour.
No skeuomorphic wood, brass, or leather. Status always pairs colour with an icon and a word.

## 1. Theme

```kotlin
CabTheme(
    darkTheme = isSystemInDarkTheme(), // default
    dynamicColor = false,              // opt-in
    highContrast = false,              // first-class fallback
    reducedMotion = rememberReducedMotion(),
) {
    OpenCabApp()
}
```

- **Dynamic colour is opt-in.** The branded fallback is the default, because status colours,
  contrast, and the instrument-panel identity have to survive on every device.
- When dynamic colour is requested, `CabTheme` generates the platform scheme and validates the
  essential text/icon pairs with WCAG ratios (4.5:1 body, 3:1 large text and glyphs). If any pair
  fails, the branded scheme is used for that device.
- `highContrast` selects a hand-tuned light/dark scheme. It intentionally disables dynamic colour;
  a generated palette cannot guarantee the exhibition-glare target.
- Both modes are available in light and dark.

`CabTheme` (the object) exposes read-only tokens:

| Accessor | Contents |
| --- | --- |
| `CabTheme.colorScheme` | The active Material `ColorScheme` |
| `CabTheme.extendedColors` | Fixed semantic status colours (live, caution, offline, disabled) |
| `CabTheme.typography` | `CabTypeScale` with tabular numeric roles |
| `CabTheme.shapes` | `CabShapeScale` including pill, control, panel, sheet |
| `CabTheme.spacing` | 4 dp grid (`xxs`…`xxl`, page, gutter, card padding) |
| `CabTheme.sizing` | Touch, arc, icon, and content-width tokens |
| `CabTheme.elevation` | Tonal elevation levels and surface roles |
| `CabTheme.motion` | `CabMotion` springs, durations, and reduced-motion collapse |
| `CabTheme.haptics` | `CabHaptics` token dispatcher |

## 2. Tokens

### Colour roles

The branded palettes define the full Material 3 role set, including all six surface-container
roles. Signal colours are **outside** the dynamic palette so a generated theme cannot turn
"connected" into an arbitrary hue:

| Token | Meaning |
| --- | --- |
| `extendedColors.live` / `liveContainer` | Link is up |
| `extendedColors.caution` / `cautionContainer` | Reconnecting or track power off |
| `extendedColors.offline` / `offlineContainer` | Link is down |
| `extendedColors.stop` / `onStop` | Destructive stop and release actions; stays red in dark mode |
| `extendedColors.disabledContent` | Non-interactive readouts and keys |

Colour is never the only carrier. `StatusPill`, `SpeedDisplay`, and `FunctionKey` all add an icon,
a word, or a state description.

### Typography

The Material type scale is tuned for glance reading: large display sizes, semibold titles, and
comfortable body line heights. Numeric roles add `fontFeatureSettings = "tnum"`:

| Role | Use |
| --- | --- |
| `speedValue` | Speed numeral, auto-sized 40–96 sp |
| `speedUnit` / `speedCaption` | Unit and last-known/caption line |
| `address` | Locomotive address, tabular bold |
| `readout` | Tabular body copy and diagnostic values |
| `readoutSmall` | Tabular supporting values |
| `functionKey` | Function labels |
| `status` | Status pill and direction word |
| `sectionTitle` | Section headings |

Tabular figures keep `11 → 88` and `1111 → 8888` from shifting horizontally. The speed numeral is
never animated; only the arc eases.

### Shape, spacing, and touch

| Token | Value |
| --- | --- |
| Shape scale | 4 / 8 / 12 / 16 / 28 dp |
| `pill` | Fully rounded |
| `control` | 14 dp |
| `panel` | 18 dp |
| `sheet` | 28 dp top corners |
| `touchMin` | 48 dp |
| `touchSecondary` | 64 dp |
| `touchPrimary` | 72 dp (upper bound for touch targets) |
| `functionKey` | 56 dp |
| `controlDeck` | 72 dp (Direction/Stop deck) |
| `leverTrackWidth` | 16 dp |
| `leverHandleWidth` / `leverHandleHeight` | 112 / 60 dp (adapts down on narrow bays) |
| `speedArcCompact` / `speedArcMax` | 232 / 320 dp |
| `contentMaxWidth` | 640 dp |

Spacing is a 4 dp grid: 4, 8, 12, 16, 24, 32, 48. Components use `CabTheme.spacing`, not literals.

### Tonal elevation

`CabElevation` maps semantic levels to Material surface containers rather than shadows:

| Level | Surface |
| --- | --- |
| `Flat` | `surface` |
| `Raised` | `surfaceContainerLow` |
| `Overlay` | `surfaceContainerHigh` |
| `Modal` | `surfaceContainerHighest` |

The drive surface stays flat; the throttle/Stop surface and cards use `Raised`; sheets use `Modal`.

### Motion

Material 3 1.4.0 makes its motion API internal (see §7), so `CabMotion` mirrors the generated
expressive and standard spring tokens:

| Scheme | Default spatial | Fast spatial | Slow spatial |
| --- | --- | --- | --- |
| Expressive | 0.8 / 380 | 0.6 / 800 | 0.8 / 200 |
| Standard | 0.9 / 700 | 0.9 / 1400 | 0.9 / 300 |

Effects springs are 1.0 damping at 1600 / 3800 / 800 stiffness. Named durations:

| Token | Value |
| --- | --- |
| `arcFill` | 120 ms linear |
| `pressFeedback` | 90 ms |
| `fadeThrough` | 300 ms |
| `reconnectIn` / `reconnectOut` | 200 / 150 ms |

`reducedMotion` (system animator scale 0, or an explicit argument) collapses every spring and
duration to `snap()`. Haptics are independent of reduced motion.

### Haptics

`CabHaptics` maps product moments to platform haptics:

| Token | Platform |
| --- | --- |
| `Detent` | `SegmentTick` |
| `Limit` | `LongPress` (a stronger detent at 0/max) |
| `Confirm` | `Confirm` |
| `Reject` | `Reject` |
| `ToggleOn` / `ToggleOff` | `ToggleOn` / `ToggleOff` |

Haptics are gated by the caller's settings toggle and are never the only feedback channel.

## 3. Components

| Component | API surface | Semantics |
| --- | --- | --- |
| `StatusPill` | `status`, `attempt` | One merged node: "Connected", "Reconnecting, attempt 3", "Offline" |
| `SpeedDisplay` | `percent`, `direction`, `stale`, `enabled`, `caption`, `arcSize` | Progress semantics + state description ("84 percent, forward") |
| `FunctionKey` | `label`, `state`, `onClick`, `contentDescription`, `latching`, optional `onPressStart`/`onPressEnd` | Checkbox for latching, button for momentary; press-and-hold for momentary touch, click action for TalkBack; On/Off/Momentary/Unavailable |
| `SectionCard` | `title`, `subtitle`, `trailing`, content | Heading on the title |
| `EmptyState` | `title`, `message`, `icon`, `action` | Heading on the title; icon is decorative |
| `InlineError` | `title`, `message`, `onRetry`, `retryLabel` | Polite live region on the message |
| `ConfirmSheet` | `visible`, copy, confirm/dismiss callbacks, `destructive` | Modal sheet; heading; explicit cancel |
| `PrimaryActionButton` | `text`, `onClick`, `enabled`, `loading`, `icon`, `size`, `tone` | Button; `Destructive` tone for Stop/release; "Loading" state description while busy |
| `SecondaryActionButton` | `text`, `onClick`, `enabled`, `icon`, `contentColor` | 48 dp text action; keeps secondary rows aligned with primary controls |
| `CabScaffold` | `title`, `subtitle`, `onBack`, `actions`, content padding | Top bar grows with font scale; back button named |
| `CabNavigationSuite` | destinations, selection, navigation callback, visibility | Adaptive bottom bar/rail |
| `CabPredictiveBackContainer` | `enabled`, `onBack`, content | Draws system back progress |

`SpeedDisplay` is presentation only. It accepts no drag input and has no command side effects;
the interactive throttle will wrap it or replace the arc surface.

The hero throttle assembly is feature-owned (`:feature:throttle`) because its drag, detents, and
control deck are one interaction model. It composes only design-system tokens: tonal panel,
`surfaceContainerLowest` readout window, `stop`/`onStop` for Stop, `controlDeck` and `lever*`
sizing, tonal elevation, press-feedback motion, and the haptic vocabulary.

`FunctionKey` off keys use the lowest tonal surface with a hairline border so a function grid stays
quieter than the throttle. Active and momentary keys keep their filled tones.

### Semantics notes

- `StatusPill` uses `clearAndSetSemantics` so TalkBack gets one node, not an icon plus text.
- `FunctionKey` sets `stateDescription` and uses `selectable` (checkbox) or `clickable` (button),
  so state is not colour-only.
- `InlineError` marks only the message row as a polite live region; the retry action stays a
  separate focusable control.
- `SpeedDisplay` exposes `progressBarRangeInfo` and a state description. It deliberately has no
  custom actions; those belong to the interactive throttle.

## 4. Adaptive navigation

`CabNavigationSuite` wraps `NavigationSuiteScaffold`:

- **Compact width** → `ShortNavigationBarCompact` (bottom bar).
- **Medium and expanded width** → `WideNavigationRailCollapsed` (rail).
- **Non-top-level destinations** → `NavigationSuiteType.None`; the suite animates out and content
  keeps the window.

Insets are handled once. The suite consumes the navigation-bar or rail inset; `CabScaffold`
consumes the status-bar inset in its top bar. Screens apply the returned `PaddingValues` and do not
add system-bar padding themselves.

Predictive back is enabled at the activity level (`enableOnBackInvokedCallback`). The app wraps
its `NavHost` in `CabPredictiveBackContainer`, which renders the system back progress as a subtle
scale/fade and then pops the graph. Material `ModalBottomSheet` supplies its own predictive-back
behaviour for sheets.

## 5. Previews

`@CabPreview` renders each component in six configurations:

1. phone portrait, light, 100 % font
2. phone portrait, dark, 100 % font
3. phone portrait, light, 200 % font
4. phone landscape, dark, 100 % font
5. tablet, light, 100 % font
6. tablet, dark, 200 % font

`CabPreviewSurface` provides the theme and a background. Feature screens use the same annotation.

## 6. Tests

- `:core:designsystem:testDebugUnitTest` checks WCAG contrast for all four branded schemes and the
  motion token behaviour (spring vs. snap, linear 120 ms arc).
- `:core:designsystem:assembleDebugAndroidTest` compiles Compose semantics tests covering the
  status pill, function-key selection, speed progress/unknown state, disabled primary action, and
  the confirmation content.
- No screenshot-test harness is configured in this project. AGP 9.5 ships screenshot-test
  plumbing, but the external `com.android.compose.screenshot` preview-renderer plugin and its
  source set are not wired into the build, so no golden images are produced. The six preview
  variants above are the visual review surface until that harness is adopted.

## 7. Pinned-version gaps (Material 3 1.4.0)

Compose BOM `2026.06.01` resolves Material3 **1.4.0** and Compose UI/foundation **1.11.4**. The UX
spec and ADR were written against `androidx.compose.material3` 1.5.x; the implementation follows
the pinned artifact, not the prose.

| Expected API | Status in 1.4.0 | What OpenCab does |
| --- | --- | --- |
| `MaterialExpressiveTheme` | `internal` | Uses public `MaterialTheme(colorScheme, typography, shapes)` |
| `MotionScheme` / `MotionScheme.expressive()` / `.standard()` | `internal` | `CabMotion` mirrors the generated spring values |
| `MaterialTheme.motionScheme` | `internal` | `LocalCabMotion` + `CabTheme.motion` |
| `ShortNavigationBar`, `WideNavigationRail`, expressive `NavigationSuiteType` values | Public | Used by `CabNavigationSuite` |
| `TextAutoSize` / `TextAutoSize.StepBased` | Public (foundation 1.11.4) | Used for the speed numeral |
| `HapticFeedbackType.Confirm` / `.Reject` / `.SegmentTick` | Public | Used by `CabHaptics` |
| `LoadingIndicator` (shape-morph), `ButtonGroup`, `SplitButton`, wavy progress | Not present | `PrimaryActionButton` uses `CircularProgressIndicator`; no wavy progress on Drive |

The `internal` declarations are visible in the published JVM bytecode but rejected by the Kotlin
compiler from another module. Forking the library or using reflection would be worse than
mirroring six spring pairs. When Material3 makes the expressive theme public, `CabMotion` can
delegate to it and the mirrored constants can be deleted without changing component call sites.

Screenshot tests will become worthwhile when the preview-renderer plugin is adopted; at that point
the existing `@CabPreview` matrix is the input set.

## 8. File map

```
core/designsystem/src/main/kotlin/dev/opencab/core/designsystem/
  component/    StatusPill, SpeedDisplay, FunctionKey, SectionCard, EmptyState,
                InlineError, ConfirmSheet, PrimaryActionButton, SecondaryActionButton, CabScaffold
  navigation/   CabNavigationSuite, CabPredictiveBackContainer
  preview/      CabPreview, CabPreviewSurface
  theme/        Color, CabContrast, Type, Shape, Spacing, Elevation, Motion, Haptics, Theme
```
