# Drive screen

**Status:** Step 7 implementation · **Related:** [`throttle-engine.md`](throttle-engine.md) ·
[`ux/ux-spec.md`](ux/ux-spec.md) §4.2, §7, §10–11 · [`design-system.md`](design-system.md)

`:feature:throttle` is the product surface. It is optimised for one hand, peripheral vision, and
gloves: the locomotive, direction, step count, and link state are glanceable; Stop never moves;
nothing that drives the train is behind a scroll.

## Layout

| Window | Composition |
| --- | --- |
| Compact portrait | Loco header → inline banner/issue → lever panel (step readout, vertical lever, nudge column) → Direction + Stop row → collapsible function grid. |
| Phone landscape | Header → inline banner → two panes: lever and a permanently open function grid, with a bottom bar whose Direction and Stop controls are anchored to the right thumb. Nothing in the driving area scrolls. |
| Tablet (≥ 600 dp wide) | Same two-pane composition with wider function cells and the same bottom-right control bar. |

The lever panel is the dominant element in every layout. The function grid scrolls inside its own
pane when it must; the driving controls do not.

The speed readout always shows **steps** (`42 / 128`). A calibrated session may add a scale-speed
estimate. Estimates are visibly marked `est.` and carry an accessible "Estimated scale speed"
description; they are never presented as measured speed.

## Interaction contract

- **Grab the handle, not the background.** Only the 96 × 56 dp handle has a pointer handler. A drag
  starting on the track is a no-op, so a stray touch cannot command a speed jump. The grab offset is
  preserved, and the lever maps absolute travel between the ends of the rail.
- **Nudge, then stop.** `+1` and `−1` move exactly one speed step. Stop is a 64 dp destructive
  primary action in the thumb zone and sends a normal stop (not a DCC E-stop).
- **Direction while moving asks first.** At step 0 it changes immediately with a confirm haptic. At
  any speed above 0 it opens "Stop, then reverse?", which sends stop and then direction.
- **Release is deliberate.** The header menu's release action always opens a confirmation sheet;
  while moving, the copy states that it stops the train before freeing the address.
- **Functions.** Keys show the server label, active fill plus check mark, and an `On`/`Off`/momentary
  state description. Latching keys toggle. Momentary keys use press-and-hold on touch and still work
  through TalkBack's click action.
- **Link loss is a mode, not a screen swap.** A reconnecting, stale, failed, or power-off banner is
  inline below the header. The lever, functions, nudge, and Direction are disabled in place; the
  layout does not unmount, and Stop stays visible. An in-use conflict is an inline card with
  `Take control`.
- **Release/steal never auto-confirm.** The user must choose the destructive action.

## Performance

- Lever position is local `LeverState` (`mutableFloatStateOf`), not engine state. Engine writes are
  coalesced to 10 Hz, and a drag never waits for the network.
- The handle moves with `graphicsLayer { translationY }`, so a drag updates a layer instead of
  remeasuring the screen. Only the step readout and lever semantics leaf recompose per step.
- The screen reads immutable `DriveUiState`; the engine publishes at 10 Hz, and unchanged regions
  are skipped by Compose.
- Function keys and banners use the design-system tone/contrast tokens. No continuous animation
  runs on the screen; reduced motion collapses the banner transition.

## Haptics

`DriveHaptics(enabled, intensity)` gates the design-system vocabulary:

| Moment | Token |
| --- | --- |
| Every integer step during a drag or nudge | `Detent` |
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

- connected;
- reconnecting (last known speed, attempt 3);
- stale (paused, last known);
- in-use conflict;
- maximum speed;
- active functions;
- long locomotive and function labels;
- no acquired locomotive.

Each preview is rendered through `@CabPreview` (phone portrait light/dark, 200 % font, phone
landscape, tablet, tablet dark 200 %). There is still no screenshot-test harness; previews are the
visual review surface.

## Tests

- `:feature:throttle:testDebugUnitTest` covers link mapping, in-use/stale states, track power,
  scale rounding, haptic gating, and address/function projection.
- `DriveScreenTest` (instrumentation) covers step/Stop presence, nudge values, background no-op,
  handle drag, `setProgress`, disconnecting controls, in-use recovery, stop-then-reverse
  confirmation, release confirmation, maximum-speed marking, active keys, momentary
  press-and-hold, and long labels.

## Known limits

- No roster picker yet: the screen renders the first acquired slot. Step 8 supplies acquisition.
- Calibration, speed cap, hold-to-run, and haptic intensity are represented in the model but have
  no settings UI yet.
- Multi-throttle and consists remain Step 9.
