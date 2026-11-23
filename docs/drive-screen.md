# Drive screen

**Status:** Step 7 implementation · **Related:** [`throttle-engine.md`](throttle-engine.md) ·
[`ux/ux-spec.md`](ux/ux-spec.md) §4.2, §7, §10–11 · [`design-system.md`](design-system.md)

`:feature:throttle` is the product surface. It is optimised for one hand, peripheral vision, and
gloves: the locomotive, direction, step count, and link state are glanceable; Stop never moves;
nothing that drives the train is behind a scroll.

## Layout

The lever assembly is one instrument bay: a recessed step readout, the physical lever, and the
Direction/Stop deck share a single tonal surface. The lever is the visual anchor; the readout is a
machined window beside it, and the controls are part of the assembly rather than a toolbar.

| Window | Composition |
| --- | --- |
| Compact portrait | Loco header → inline banner/issue → instrument bay with a **Throttle / Functions** tab row. The Throttle tab holds the readout window and lever; the Functions tab holds the full-width function grid. The Direction + Stop deck is pinned below both tabs, so Stop is never hidden. |
| Phone landscape | Header → inline banner → two panes. The instrument bay is the left pane: the readout window sits above the Direction + Stop deck in the left column, and the lever runs the full height of the bay beside them. The function grid is the right pane, permanently open and without a heading. Nothing in the driving area scrolls. |
| Tablet (≥ 600 dp wide) | The same two-pane composition with a compact, centred instrument bay. The readout has a capped height, the lever spans the bay, and the function grid uses a quieter flat surface. Portrait tablets keep the bay full height; landscape tablets cap it so it reads as a mounted console rather than a stretched column. |

In compact portrait the function grid is a peer tab, not a footer: switching to Functions replaces the
readout/lever stage but leaves the Direction/Stop deck pinned. This keeps the driving surface
uncluttered while guaranteeing the destructive stop control is always one tap away. The portrait
bay is **full-bleed** to the screen edges, while the deck is inset from the display's rounded
corners and system gesture area so the keys are never clipped. Landscape and tablet keep the
permanently open function pane beside the throttle.

The lever rail runs the full height of the instrument bay and deliberately has no textual `MAX` /
`0` end labels. The step numeral, direction, detent ticks, and the readout's `MAX` badge carry the
endpoints.

The readout hierarchy is deliberate:

1. **Current step** is the dominant numeral.
2. **Total steps** and the **direction badge** are secondary and sit directly below it.
3. The **scale estimate** is tertiary, prefixed `est.`, separated by a hairline, and captioned
   `Scale estimate`. It is never presented as measured speed.

The function grid is deliberately quieter than the throttle: no heading in the wide pane, flat
surface, low-contrast off keys, and a wider cell size at large font scales so labels do not wrap
into one-character fragments.

### Large type and adaptive composition

- At 150 % font scale or above on a narrow window (< 600 dp), the status pill moves below the
  locomotive name and address so neither line is truncated. On wide windows it stays inline.
- The readout derives its numeral size from the available height, so the step stays inside its
  window at 200 % font. The estimate is hidden when the window is too short rather than clipped.
- The portrait tab labels auto-size so `Throttle` and `Functions` stay on one line at 200 % font.
- The lever assembly chooses its composition from its own aspect ratio and size: compact portrait,
  phone landscape, or a capped large-screen console.


## Interaction contract

- **Grab the handle, not the background.** Only the handle has a pointer handler (up to
  112 × 60 dp, from `CabTheme.sizing`). A drag starting on the rail, ticks, or the surrounding bay
  is a no-op, so a stray touch cannot command a speed jump. The grab offset is preserved, and the
  lever maps absolute travel between the ends of the rail. The rail spans the full bay height with
  no `MAX`/`0` text labels; the handle is clamped so it can never leave the instrument.
- **Visible controls are Direction and Stop.** The lever assembly has no `+1`/`−1` buttons. Both
  controls live in a 72 dp deck inside the bay: full-width beneath the readout and lever in
  portrait, and directly beneath the readout in landscape. The direction key pairs an arrow with
  `FWD`/`REV`; Stop uses the dedicated `stop` / `onStop` extended-colour pair so it stays an
  unmistakable red in dark mode. Arrow keys still move one step (Shift+Arrow ten) for keyboard and
  TalkBack users. Stop is a destructive primary action and sends a normal stop (not a DCC E-stop).
- **The readout is a window, not a second control.** It is presentation only. The lever owns
  drag/`setProgress`; the deck owns Direction and Stop.

- **Direction while moving asks first.** At step 0 it changes immediately with a confirm haptic. At
  any speed above 0 it opens "Stop, then reverse?", which sends stop and then direction.
- **Release is deliberate.** The header menu's release action always opens a confirmation sheet;
  while moving, the copy states that it stops the train before freeing the address.
- **Functions.** In compact portrait, Functions is a tab beside Throttle in the instrument bay; the
  Direction/Stop deck stays pinned below. Keys show the server label, active fill plus check mark,
  and an `On`/`Off`/momentary state description. Latching keys toggle. Momentary keys use
  press-and-hold on touch and still work through TalkBack's click action. Wide layouts keep the
  grid permanently visible in its own pane without a heading.
- **Link loss is a mode, not a screen swap.** A reconnecting, stale, failed, or power-off banner is
  inline below the header. The lever, functions, and Direction are disabled in place; the layout
  does not unmount, and Stop stays visible. An in-use conflict is an inline card with
  `Take control`.
- **Release/steal never auto-confirm.** The user must choose the destructive action.

## Performance

- Lever position is local `LeverState` (`mutableFloatStateOf`), not engine state. Engine writes are
  coalesced to 10 Hz, and a drag never waits for the network.
- The handle is positioned with a layout-phase `offset` and scaled with `graphicsLayer`. A drag
  repositions a leaf without remeasuring the screen, and the semantics bounds follow the visual
  handle for TalkBack and UI tests. Only the step readout and lever semantics leaf recompose per
  step.
- The screen reads immutable `DriveUiState`; the engine publishes at 10 Hz, and unchanged regions
  are skipped by Compose.
- Function keys and banners use the design-system tone/contrast tokens. No continuous animation
  runs on the screen; reduced motion collapses the banner transition.

## Haptics

`DriveHaptics(enabled, intensity)` gates the design-system vocabulary:

| Moment | Token |
| --- | --- |
| Every integer step during a drag or keyboard nudge | `Detent` |
| 0 or maximum step | `Limit` |
| Direction change, stop, release confirmed | `Confirm` |

System haptics and the future intensity setting both gate output; haptics are never the only
feedback channel.

## TalkBack

- The lever is one node with:
  - `progressBarRangeInfo(0f..1f, steps = stepCount − 1)`;
  - a `setProgress` action;
  - `stateDescription = "42 of 128 steps, Forward"`;
  - custom actions for Stop, Forward, and Reverse.
- Arrow keys move one step; Shift+Arrow moves ten.
- The step readout is not a live region. Coarse announcements are debounced: a bucket change waits
  600 ms and then announces `Speed 42 of 128 steps`, so dragging does not read every tick. A drag
  that settles in the same bucket stays silent.
- Stop's state description includes the current step, so it is meaningful out of context.
- Banners are polite live regions; status remains icon + word, never colour alone.

## State model and previews

`DriveUiState` is a protocol-free screen model. `DriveStateMapping` maps the engine and connection
snapshot; `DriveViewModel` keeps the engine and navigation out of composables.

`DrivePreviewData` supplies the review matrix:

- `connected`;
- stopped (zero step);
- reconnecting (last known speed, attempt 3);
- stale (paused, last known);
- in-use conflict;
- maximum speed;
- active functions;
- the Functions tab;
- long locomotive and function labels;
- no acquired locomotive.

Each preview is rendered through `@CabPreview` (phone portrait light/dark, 200 % font, phone
landscape, tablet, tablet dark 200 %). There is still no screenshot-test harness; previews are the
visual review surface.

For device review without a live WiThrottle layout, `:feature:throttle` exposes
`DriveDebugPreview` only in its **debug source set**, and `:app` declares a debug-only
`DebugDriveActivity` in `src/debug`. Neither the activity nor the preview bridge exists in a
release build. Launch a named state with:

```bash
adb shell am start -n dev.opencab/.DebugDriveActivity --es state reconnecting
```

Supported states: `connected`, `stopped`, `reconnecting`, `stale`, `in-use`, `max`,
`active-functions`, `functions`, `long-labels`, `empty`.

## Tests

- `:feature:throttle:testDebugUnitTest` covers link mapping, in-use/stale states, track power,
  scale rounding, haptic gating, and address/function projection.
- `DriveScreenTest` (instrumentation) covers step/Stop presence, the right-hand Direction and Stop
  controls, background no-op, handle drag, `setProgress`, disconnecting controls, in-use recovery,
  stop-then-reverse confirmation, release confirmation, maximum-speed marking, active keys,
  momentary press-and-hold, long labels, the control deck staying inside the instrument bay, the
  landscape assembly stacking the deck under the readout while the lever spans the bay height, and
  the portrait Functions tab hiding the lever without hiding the pinned Stop deck.


## Known limits

- Acquisition comes from the roster picker or roster row; the Drive menu and empty state open the
  picker. The screen still renders the last desired slot, so replacing a locomotive highlights the
  new one immediately.
- Calibration, speed cap, hold-to-run, and haptic intensity are represented in the model but have
  no settings UI yet.
- Multi-throttle and consists remain Step 9.
