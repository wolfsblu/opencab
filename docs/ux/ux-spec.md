# OpenCab — UX Specification

**Status:** Draft v1 (Step 0 deliverable) · **Date:** 2026-11-20
**Depends on:** [`../product-brief.md`](../product-brief.md) · **Architecture:** [`../adr/0001-platform-and-stack.md`](../adr/0001-platform-and-stack.md)
**Design system:** Material 3 Expressive (`androidx.compose.material3` 1.5.x, `MaterialExpressiveTheme`)

---

## 1. Design intent

### 1.1 The core insight

The phone is not the interface. **The train is the interface**; the phone is a remote control held at an angle, half in peripheral vision, sometimes in gloves, sometimes by a stranger. Every decision below is downstream of that.

This is also where Material 3 Expressive's own research helps rather than hinders: M3 Expressive showed participants locating key UI elements **up to 4× faster** than in equivalent non-expressive layouts, and erased much of the age-related gap in fixation time. For a control surface that is read in glances, that is the single most relevant finding in the design system. We use expressive *hierarchy* aggressively and expressive *motion* conservatively (§11).

### 1.2 Five principles

| # | Principle | Test we apply in review |
|---|---|---|
| **P1** | **Glanceable.** Four facts, maximum, on the drive screen: loco, direction, speed, link. | Squint test at 60 cm, 1 second. |
| **P2** | **One-thumb.** Every primary action reachable without re-gripping. | Reach-envelope overlay, right and left hand. |
| **P3** | **Truthful.** The UI never asserts a state it has not confirmed. | Every link transition has a named UI state. |
| **P4** | **Stop is sacred.** Stopping never costs a confirmation, a hold, or a menu. | One tap from any drive state, including mid-drag. |
| **P5** | **Reconnect is weather.** A drop is a modality, not a failure. | Drive screen never unmounts on a blip. |

### 1.3 Where we deliberately do *less* than M3 Expressive

M3 Expressive encourages bounce, shape morphing, and variable geometry. We adopt the hierarchy and restraint-free colour, and refuse the bounciness **where it would imply physical motion that did not happen**:

- No overshoot on the speed readout or its arc. A spring that overshoots 84% → 88% → 84% tells the operator the train sped up and slowed down. It did not.
- No wavy progress indicators on the drive screen. Wavy reads as "flow/speed", competes with the actual speed display, and is the kind of decorative motion that costs battery at an all-day exhibition.
- Shape morphing is reserved for buttons and the connect→drive hero transition, never for numeric state.

M3's own guidance that *shape is versatile, not semantic* supports the accessibility rule in §10: no status may be encoded in shape or colour alone.

---

## 2. Screen inventory

| # | Screen | Role | Entry | Exit | Chrome |
|---|---|---|---|---|---|
| **S1** | **Connect** | Full-screen gate. No app content exists behind it. | Launch, or losing the layout and choosing to leave Drive | Successful connect → Drive; Back → exit app | No nav bar, no FAB, no drawer |
| **S2** | **Drive** | Home destination. The product. | Successful connect; nav bar | Nav bar to Roster/Settings; Back → release prompt; link loss → overlay | Top bar (status + loco) + nav bar |
| **S3** | **Roster** | Secondary destination: pick or enter a loco. | Nav bar; loco chip on Drive | Pick → back to Drive (acquired) | Top bar with search; nav bar |
| **S4** | **Settings** | Secondary destination. | Nav bar | Nav bar / Back | Top bar; nav bar |
| **S5** | **Functions** | Modal bottom sheet, not a destination. F0–F28. | Control on Drive or swipe-up handle | Dismiss → Drive | Sheet, no nav |
| **S6** | **Loco actions** | Modal sheet: release, speed cap, rename locally, diagnostics for this loco. | Overflow on the loco chip | Dismiss | Sheet |
| **S7** | **Diagnostics** | Behind Settings. Raw protocol log, versions, link timings. | Settings | Back | Top bar; no nav |

Deliberately **not** screens: a "connecting…" screen (it is a state of S1), an "error" screen (errors are states of the screen that owns the action), and a "release confirmation" screen (it is a small sheet, and only when a loco is held).

### 2.1 Navigation map

```
                         ┌──────────────────────────────┐
   launch ──────────────►│  S1  CONNECT  (gate)         │
                         │  discover / manual / recent  │
                         └───────┬──────────────┬───────┘
                                 │ connected    │ version < 2.0
                                 ▼              ▼
                    ┌────────────────────┐   (blocked state,
                    │  S2  DRIVE  (home) │    no way forward)
                    │  ┌──────────────┐  │
                    │  │ link overlay │  │◄──── dropped link
                    │  └──────────────┘  │      (stays mounted)
                    └───┬────┬───────┬───┘
             nav bar    │    │       │   nav bar
                  ┌─────┘    │       └──────┐
                  ▼          ▼ sheet        ▼
          ┌───────────┐ ┌────────────┐ ┌───────────┐
          │ S3 ROSTER │ │ S5 FUNCS   │ │S4 SETTINGS│
          └─────┬─────┘ └────────────┘ └─────┬─────┘
                │ acquire                    ▼
                └───────────► S2    ┌────────────────┐
                                    │S7 DIAGNOSTICS  │
                                    └────────────────┘
```

**Navigation rules**

1. **Drive is the start destination** once a session exists. The graph is `Connect → (Drive ⇄ Roster, Settings)`, not `Connect → Home → Drive`.
2. **Connect is a gate, not a screen.** It has no nav bar, no back stack into Drive, and no partial states that leak. If the session ends, Drive is *covered*, not replaced, so a blip can restore it in place.
3. **Roster is a verb, not a place.** Its only job is to return a loco. After a successful acquire it returns to Drive. It does not become a browsing surface with its own detail pages.
4. **Functions is a sheet** because it is a mode you are in for five seconds, not a place you navigate to. Sheets also keep the throttle physically on screen behind them (the arc stays visible above the sheet's 60% detent) so the operator never loses speed context.
5. **Back from Drive while holding a loco** opens the release sheet (`Release 3412?`). Back again dismisses. We never release a loco as a side effect of a back gesture.
6. **Back from Connect** exits the app only if no session exists; otherwise it is consumed.
7. **Predictive back** is supported and previews the sheet/screen, never the release action.
8. **State restoration** survives process death: endpoint, last loco address, and scroll positions are restored; the *link* is not faked — it reconnects and re-acquires, showing the reconnect UI.

---

## 3. Primary flow: discover → connect → pick → acquire → drive → release

Timings are targets on a healthy LAN, measured from tap to confirmed state.

### 3.1 Happy path

| Step | User | System | Wire | Budget |
|---|---|---|---|---|
| 1 | Opens app | Connect gate, discovery starts immediately, skeletons for list rows | mDNS browse `_withrottle._tcp` | results < 3 s |
| 2 | Sees one or two named layouts | Rows show server name from `Ht`, host:port, and a relevance hint ("Last used 2 h ago") | — | — |
| 3 | Taps "Junction Yard" | Row morphs into a connecting state; row stays put, no screen change | TCP connect | < 1 s |
| 4 | — | Identifies itself on connect, because some servers stay silent until the client speaks | `N<cab name>`, `HU<stable uuid>` | immediate |
| 5 | — | Reads the greeting and gates on version | expect `VN`, `HT`, `Ht`, `PPA`, `RL` within 2 s | < 2 s |
| 6 | — | Enables server-side liveness once the server states its timeout | `*+` (see §7.11) | immediate |
| 7 | Lands on Roster (first run) or Drive (returning, last loco) | Roster pre-filtered to "Recent" if we have history | — | < 300 ms |
| 8 | Taps "3412 Black Five" | Row shows an acquiring spinner; row does not navigate yet | `M0+L341<;>L341` | < 800 ms |
| 9 | — | Grant received; screen becomes Drive with speed 0 | server replies `M0+L341`, `V0`, `R1`, `s1` | — |
| 10 | Puts thumb on the dial, drags up | Speed follows the thumb, arc fills, tick haptics at 5% | `V<n>` coalesced at 10 Hz | finger→loco ≤ 150 ms |
| 11 | Lifts thumb | Value holds (default) | final `V<n>` flushed | — |
| 12 | Taps **STOP** | Speed animates to 0 over 400 ms, control confirms | `V0` | ≤ 100 ms to send |
| 13 | Overflow → Release | Sheet: `Release 3412?` with `Release` / `Keep driving` | `V0` then `M0-L341<;>r` | ≤ 1 s |

**Returning-user path** collapses steps 1–7: the gate remembers the last layout and last loco, so it is `launch → Drive (auto-reconnect) → tap the remembered loco chip → acquire → drive` — deliberately not auto-acquire. Silently grabbing a loco on launch is how a club night starts with two people on one address.

### 3.2 The acquire result is the product's most important moment

Acquisition can end in four ways, and each has a distinct, non-modal presentation:

| Result | UI | Notes |
|---|---|---|
| **Granted** | Drive screen, brief `Confirm` haptic, no toast | The transition itself is the success message |
| **Blocked — held elsewhere** | Inline sheet on the roster row: `3412 is in use by another throttle` · `Take control` / `Choose another` | See §6.5 |
| **Denied — bad address** | Inline error on the row: `The layout doesn't recognise that address` | From `HM`; keep the typed address so it can be corrected |
| **No answer** | Row shows a timeout state with `Try again` | Do not navigate; do not clear the list |

No acquisition failure is ever a full-screen error, a toast, or a silent no-op.

---

## 4. Screen specs

### 4.1 S1 — Connect (gate)

```
┌──────────────────────────────────────┐
│                                      │
│   OpenCab                            │  displaySmall, emphasized
│   Find your layout                   │  bodyLarge, muted
│                                      │
│   ◌ Searching…                       │  LoadingIndicator (shape-morph)
│                                      │
│   ┌────────────────────────────────┐ │
│   │ Junction Yard           ▸      │ │  2-line item, 72 dp
│   │ 192.168.1.24:12090             │ │  labelMedium, muted
│   │ JMRI 5.8 · last used 2 h ago   │ │
│   └────────────────────────────────┘ │
│   ┌────────────────────────────────┐ │
│   │ Test Bench                     │ │
│   │ 192.168.1.31:12090             │ │
│   └────────────────────────────────┘ │
│                                      │
│   ┌────────────────────────────────┐ │
│   │ Enter address manually       ⌨ │ │  outlined, 56 dp
│   └────────────────────────────────┘ │
│   Recent:  Home · Club layout        │  text buttons, 48 dp
└──────────────────────────────────────┘

```

**States:** `Searching` · `Found(n)` · `NothingFound` (after 8 s, with manual entry promoted above the fold) · `Connecting(endpoint)` · `Failed(reason)` · `Blocked(version)`.

**Behaviour**
- Discovery starts on first composition; it does not require a tap. The search affordance is an *interruption*, not the entry point.
- Found rows appear progressively; the list is stable (no reordering while the user reads).
- Manual entry and Recent are *always* visible, never hidden behind "More". This is a direct consequence of the JMRI documentation's own warning that discovery frequently does not work.
- The permission request is deferred until we have a concrete reason: first launch shows the gate with discovery already running and a single explanatory line if the OS blocks it.
- Pull-to-refresh is not used; a `Search again` text button appears only in `NothingFound` and after a failure.

### 4.2 S2 — Drive (home destination)

**Portrait (compact)**

```
┌──────────────────────────────────────┐
│ ● Junction Yard             3412   ⋮ │  56 dp top bar
│                                      │
│  ┌────────────────────────────────┐  │
│  │ 3412 · Black Five        84 %  │  │  loco chip, 64 dp
│  │ FORWARD ▲                      │  │
│  └────────────────────────────────┘  │
│                                      │
│           ╭─────────────╮            │
│          ╱               ╲           │  speed arc, 232 dp
│         │      84         │          │  centre at ~62% height
│         │      %  ▲       │          │
│          ╲               ╱           │
│           ╰─────────────╯            │
│            drag ↕                    │  hint, fades after first use
│                                      │
│   ┌───────────┐   ┌───────────────┐  │
│   │   STOP    │   │  ⇅  DIRECTION │  │  72 dp / 64 dp
│   └───────────┘   └───────────────┘  │
│  ┌────────────────────────────────┐  │
│  │  Drive    Roster    Settings   │  │  ShortNavigationBar, 80 dp
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘

```

**Landscape (compact height, medium width)**

```
┌────────────────────────────────────────────────────────┐
│ ● Junction Yard   3412 · Black Five ▲                ⋮ │
├──────────────────────────┬─────────────────────────────┤
│                          │                             │
│         ╭──────────╮     │ F0   F1   F2   F3           │
│         │          │     │ F4   F5   F6   F7           │
│         │   84 %   │     │ F8   F9   F10  F11          │
│         │    ▲     │     │                             │
│         ╰──────────╯     │ scrolls · 4 of 8            │
│                          │                             │
├──────────────────────────┴─────────────────────────────┤
│  [ STOP ]                                    [ ⇅ DIR ] │
└────────────────────────────────────────────────────────┘
```

Note that landscape keeps Stop at the bottom **right** and Direction at the bottom **right-most** — both anchored to the thumb, not to visual symmetry.

**Expanded (tablet)**

```
┌────────┬─────────────────────────┬───────────────────────┐
│ rail   │ DRIVE                   │ ROSTER (supporting)   │
│  ○     │  ┌────────────────────┐ │  search               │
│  ●     │  │ 3412 Black Five    │ │  3412  Black Five  ★  │
│        │  └────────────────────┘ │  2201  Class 47       │
│        │      ( speed arc )      │  088   Shunter        │
│        │   [STOP]  [DIR]         │  ── not on roster ──  │
│        │                         │  + Enter address      │
└────────┴─────────────────────────┴───────────────────────┘

```

Tablet uses a navigation rail and a supporting roster pane so switching locos does not cost a navigation transition. The roster pane is *read-and-tap*, not a second place to get lost.

**Content rules for Drive**
- Exactly four facts: loco identity, direction, speed, link status. Nothing else earns permanent space.
- The top bar's leading dot is purely decorative; status is always carried by an icon **and** a word (`● Live`), exposed as a single accessible node (§10.2).
- Speed is the largest element on screen (`displayLarge`, emphasized role, auto-sizing — see §10.3).
- Function access is one tap: the `⌃` affordance on the loco card, or a swipe up on the arc handle for a larger target. It must not occupy permanent grid space in portrait.
- No scrollable content on the drive screen in portrait. If something does not fit, it belongs in a sheet.

### 4.3 S3 — Roster

A **single list with three bands**, no tabs:

1. **Recent** (local, up to 4) — "the locos you actually drive".
2. **On this layout** (`RL` order, server labels) — with a search field pinned under the top bar once the list exceeds 12 items.
3. **Not on the roster** — one row: `Enter an address` (opens a compact keypad sheet: address + short/long segmented control).

Row anatomy: `address (labelLarge, tabular) · name (bodyLarge) · trailing state`. Trailing state is one of: `★` favourite, a `held` chip after a blocked attempt, or nothing. **We do not show "in use" before an attempt** — WiThrottle gives us no presence information, and a guessed lock icon would be a lie (P3).

Filters: a single `Favourites` filter chip. No sort control; the server's order is the layout owner's intent.

### 4.4 S5 — Functions sheet

- Grid, 4 columns portrait / 8 landscape, 56 dp cells with 8 dp gaps, labels from the server (`M...L`).
- State: filled container + `On` state description when active; outlined when off. **Not colour alone.**
- Momentary functions fire on press and release (`F1n` / `F0n`); latching ones toggle (`f1n`). Which is which comes from the server where available, else from a local per-function default of latching.
- Header: `Functions · 3412`. One row of context, then the grid.
- The sheet is modal, **except** for the global Stop bar, which renders above the scrim (§5). A sheet is never a reason to take longer to stop.
- Dismissing by drag keeps the last state; there is no "apply".
- If the server reports no labels, show `F0…F28` and put "This layout doesn't name its functions" in the header subtitle.

### 4.5 S4 — Settings

Grouped list, no more than six groups, everything defaulted sensibly:

| Group | Items |
|---|---|
| **Driving** | Hold to run (dead-man) · Speed display (% / steps) · Speed cap default · Haptics |
| **Screen** | Keep screen awake while driving · Theme (system / light / dark / high contrast) |
| **Layouts** | Saved layouts (rename, forget) · Forget all |
| **Accessibility** | Reduce motion (follow system / always) · Larger readouts · TalkBack hints |
| **Diagnostics** | Connection log, protocol version report, copy log |
| **About** | Version, licences, "OpenCab is not affiliated with JMRI" |

"High contrast" is a first-class theme, not a filter: exhibitions have glare, and dynamic colour can generate low-contrast pairs.

### 4.6 S6 — Loco actions sheet

`Release 3412` (destructive, first) · `Speed cap` (slider 10–100%, applies locally, shown as a tick on the arc) · `Rename locally` · `Emergency stop (DCC)` (bypasses the ramp: sends `X`; confirmed, because it also stops the train for anyone else on the address) · `Diagnostics for this loco`.

Release is always preceded by `V0` on the wire, and the sheet says so in one line: `Stops the train, then frees the address.`

---

## 5. Layout and hierarchy rules

- **One primary action per screen.** Drive: the throttle. Connect: the server list. Roster: the row.
- **The stop control never moves and never hides.** Same corner, same size, every layout, every state. It is the one element allowed to pierce a modal scrim: the functions sheet, the loco actions sheet, and the reconnect overlay all render *under* the Stop bar (see §7.3).
- **Sheets over screens** for transient verbs; **screens over sheets** for anything with its own back stack.
- **No nested scrolling on Drive.** Sheets may scroll; the throttle may not.
- **Elevation is used for the thing you can touch**, not for decoration: the throttle and Stop sit on `surfaceContainerHigh`; the background is `surface`.

---

## 6. Edge states

Each state below has: how we detect it, what the operator sees, what they can do, and what we must never do.

### 6.1 No server found

- **Detection:** mDNS browse yields nothing for 8 s (or the OS reports discovery unavailable).
- **Sees:** `No layouts found nearby.` · `Make sure WiThrottle is running and this phone is on the same Wi-Fi.` · primary `Enter address manually` · secondary `Search again`.
- **Do:** promote manual entry above the fold; keep the last-used layout as a one-tap row if it exists.
- **Never:** spin indefinitely; hide manual entry behind a menu; blame the user.

### 6.2 Wrong port

- **Detection:** `ECONNREFUSED` or connect timeout on an explicit endpoint.
- **Sees:** `Nothing answered at 192.168.0.31:12090.` · `Check the port in JMRI → Preferences → WiThrottle.` · if the port is not 12090, a chip: `Try 12090`.
- **Do:** keep the typed host so only the port needs editing; offer the default-port chip.
- **Never:** say "authentication failed" (there is none); scan the subnet (noise, and it looks like an attack to a club network).

### 6.3 Permission denied (discovery or local network)

- **Detection:** `NEARBY_WIFI_DEVICES` denied (API 33+), or LAN socket blocked by Android 16+ local-network protection.
- **Sees:** one line in the gate: `OpenCab needs permission to find devices on your Wi-Fi.` · `Allow` · `Enter address instead`.
- **Do:** request on first meaningful use, with the system rationale already on screen; if permanently denied, replace `Allow` with `Open settings` and keep manual entry working where the OS allows it.
- **Never:** loop the permission dialog; block a manual connection that the OS permits; use the word "permission" without saying what for.

### 6.4 Dropped connection mid-run

The most important state in the product.

- **Detection:** heartbeat send failure, socket EOF, or no server traffic for `2 ×` the negotiated heartbeat interval.
- **Sees:** Drive stays mounted. Everything desaturates to 40% via a scrim. The speed readout is replaced by `—` with the label `Last known 62%`; direction and loco stay legible. A persistent inline card at the top of the arc: `Reconnecting…` with a subtle indeterminate `LoadingIndicator`, then `Reconnecting… (3)` after the third attempt. Stop remains tappable and sends nothing (it is already true).
- **Meanwhile:** with heartbeat monitoring on (the default, §7.11) the server will have E-stopped the loco when our heartbeat lapsed. **We say so**, in the card's second line once reconnected: `The layout stopped your train when the link dropped.`
- **Auto-recovery:** backoff 0.5 / 1 / 2 / 4 / 8 s, capped at 8 s, indefinitely while the operator stays; each attempt re-runs version check, then re-acquires the last address. On success: `Confirm` haptic, speed restored to **0**, never to the last value.
- **Manual:** `Retry now` in the card; after 30 s also `Back to layouts`.
- **Never:** keep the throttle interactive; show a stale speed as if live; restore speed automatically; show a full-screen error inside the first 30 s.

### 6.5 Loco already acquired elsewhere

- **Detection:** server replies with a steal prompt (`MTS…`) after our `M+`, or an `HM` indicating the address is in use.
- **Sees:** a sheet, not a screen: `3412 is in use by another throttle.` · `Taking control will stop it for the other operator.` · destructive `Take control` · `Choose another`.
- **Do:** name the consequence in the second line; default focus on `Choose another`; haptic `Reject` on arrival at this state.
- **Never:** auto-steal; silently fail; claim to know *who* holds it (we do not); mark rows as locked pre-emptively.

### 6.6 Track power off

- **Detection:** `PPA0` at connect or any time (server pushes it).
- **Sees:** a persistent amber-toned banner on Drive and Roster: `⚡ Track power is off — trains won't move.` The drive controls stay enabled (the train may still be visible/rolling down; and power may return at any moment).
- **Do:** if the operator drags the throttle while power is off, show a one-line inline note under the arc (`Waiting for track power`) and a `Confirm`-tone haptic — not a modal, not a toast stack.
- **Never:** disable the throttle (state is not ours to own); offer a power toggle (v1 is status-only, see non-goals); hide the banner behind a sheet.

### 6.7 Server version mismatch

- **Detection:** `VN` < 2.0 (hard block, matching the de-facto client floor); `VN` > 2.x (soft).
- **Sees (hard):** `This layout is running an older WiThrottle server (1.6). OpenCab needs 2.0 or newer.` · `Back` · `Copy details`.
- **Sees (soft):** connects normally; settings/diagnostics shows `Server reports 3.1 — some features may be unavailable.` No blocking dialog.
- **Do:** feature-detect everything above 2.0; never assume a 2.x behaviour from a version number alone.
- **Never:** connect to < 2.0 "and see what happens"; the failure modes are unpredictable and look like our bug.

### 6.8 Server-sent notices (`HM`)

Non-fatal server messages appear as a single-line, dismissible `Snackbar` with the server's own text prefixed by the layout name, and are always written to the diagnostics log. Never a dialog; never swallowed.

### 6.9 Screen off / app backgrounded while holding a loco

Keep-screen-on is active while driving, so this is usually moot. If the operator backgrounds the app: the link stays up while the process lives, heartbeat continues, and on return the drive screen shows the *server's* current speed (queried with `qV`), not our cached one. If the process died, the reconnect path (§6.4) applies, and the server has already stopped the train.

---

## 7. Throttle interaction principles

This section is the heart of the product and the part most likely to cause an accident if wrong.

### 7.1 Delta-drag, never absolute-jump

The single worst failure in phone throttles is a slider that teleports to wherever you tap. On a real layout that is an instant 80% command to a train.

**Rule:** the dial is *relative*. Touching it latches the current value and an anchor point; movement is applied as a delta from that anchor. Tapping does nothing. Dragging 8 dp does almost nothing. There is no "jump to finger".

- **Press** — latch the current speed, record the pointer's Y as the anchor.
- **Move** — apply `(anchor − pointerY) × gain` to the latched value, clamped to `0…cap`.
- **Release** — send the final value once, then hold it (or start the ramp to 0 in hold-to-run mode).

The anchor is never re-based mid-drag, so a fast flick followed by a slow correction behaves predictably and the value is always a function of total thumb travel.

| Gesture | Result |
|---|---|
| Tap | **Nothing.** A deliberate no-op with no haptic. |
| Drag up / down | Speed ±, proportional to drag distance, no acceleration curve |
| Drag past 100% | Hard clamp at the cap; 8 dp of rubber-band, then a `SegmentTick` at the limit |
| Drag past 0% | Hard clamp at 0; nothing below |
| Double-tap | Optional (off by default): stop |
| Long-press | Unused. Reserved. Never required. |
| Two-finger | Unused anywhere in the app |

**Gain:** ≈ 200 dp of travel covers 0→100% in normal mode, so a full-range change needs a deliberate sweep, not a twitch. A `Fine` toggle (or drag *starting* below the arc's centre) multiplies travel by 4 for shunting.

**Quantisation:** the wire is integer 0–126; we quantise the *drag* to integer steps and display integer percent. The visual arc interpolates smoothly between steps; the number does not wobble.

### 7.2 No accidental speed jumps (the full list)

Beyond delta-drag:

- No `Slider` component on the drive screen. Material sliders are absolute-position controls; this is a relative control with a different contract.
- The throttle does not accept input while any sheet is open, while reconnecting, or during the 250 ms after an acquire (prevents a "carry-over" gesture landing on a fresh loco).
- A drag that begins outside the dial's touch region is ignored — we do not extend hit slop into the Stop button.
- Rotating the device or a configuration change preserves the current value and does not re-fire a command.
- Speed is never changed by a notification, a fold, or an incoming call.

### 7.3 Immediate stop

- Stop is a 72 dp (phone) / 64 dp (landscape) filled button, bottom-right thumb zone, identical on every layout and every state.
- Stop is **chrome, not content**: it is drawn above every sheet, overlay, and dialog. There is no state in which stopping costs more than one tap.
- One tap: sends `V0` immediately. The number snaps to 0; the arc eases over 400 ms so the change is visible without implying a physical deceleration we cannot measure.
- Stop works **during a drag**: pressing it cancels the drag, discards the pending value, and sends 0. This is the case that matters when the operator's thumb is already on the dial and something goes wrong.
- Stop does **not** release the address. Release is a separate, deliberate action with a confirmation.
- Emergency stop (`X`, DCC emergency) exists in the loco actions sheet, not on the main surface. Operators need "stop *my* train", not "cut the booster", and a second red button next to the first is how people press the wrong one.
- The server-side E-stop on heartbeat lapse is our backstop, and we tell the operator when it fires.

### 7.4 Glanceable state

Four facts, ordered by the eye:

1. **Speed** — largest glyph on screen, `displayLarge`+emphasized, centred in the arc.
2. **Direction** — arrow + word (`FORWARD`, `REVERSE`), never colour, never an arrow alone.
3. **Loco** — address first (tabular numerals), then name: `3412 · Black Five`.
4. **Link** — dot + word in the top bar (`● Live`), plus recency (`Live · 2 s`).

Everything else is transient: the speed cap tick, the function sheet, the power banner.

**No animation loops on this screen.** No pulsing dot, no breathing arc, no wavy progress. A still screen is a readable screen, and at an exhibition it is also a cooler, longer-lasting one. The only continuous animation permitted is the reconnect spinner, which is bounded and meaningful.

### 7.5 One-handed reach

- Reach envelope: a 150 dp radius centred on the bottom corner of the holding hand, and everything primary must live there.
- Portrait: Stop and Direction on the bottom row; the arc is a *drag surface* whose centre may sit higher, because dragging up starts from the bottom and the thumb does not need to reach the middle.
- Landscape: Stop bottom-right; Direction immediately beside it; functions in the upper-right free zone (rare, non-urgent).
- Tablet: drive pane is bottom-anchored in the pane, not centred in the screen, so a hand resting on the bezel can reach it.
- No primary action in the top 25% of the display. The top bar is status only, plus an overflow that duplicates anything important.
- Long-press, swipe-to-dismiss, and two-finger gestures are never the only path to a core action.

### 7.6 Latency budget

A laggy throttle causes over-correction, which causes oscillation, which looks like a badly tuned loco.

| Segment | Budget |
|---|---|
| Pointer sample → value update | ≤ 16 ms (one frame) |
| Value → command on the wire | ≤ 50 ms |
| Wire → server ack observed | ≤ 100 ms (LAN) |
| Finger → physical motion | ≤ 150 ms |

Command rate is coalesced to **10 Hz** with last-value-wins, plus an unconditional flush on pointer-up. The UI is **optimistic**: the readout shows the commanded value immediately, and a small `pending` treatment (a 2 dp dashed underline on the number) appears only if an ack is late by > 250 ms. We never block the drag on the network.

### 7.7 Direction changes

Changing direction while moving is how trains derail and gearboxes complain.

- Direction is enabled only at speed 0.
- Pressing it while moving does not change direction. It opens a one-line inline prompt under the arc: `Stop, then reverse?` with `Stop & reverse` / `Cancel`.
- `Stop & reverse` sends `V0`, waits for the value to reach 0 locally (and at most 600 ms for the server), then sends `R` with the new direction (0 or 1).
- The direction control always shows the *commanded* direction, with a `pending` state while a reversal is in flight.

### 7.8 Hold-to-run (dead-man)

Optional per session. It is the default in the exhibition hand-over mode proposed in the product brief (open question 3); until that ships, it is a per-session toggle.

- Behaviour: while the operator holds the dial, speed follows the drag; on release, speed ramps to 0 over 1.2 s (configurable 0.5–3 s) with the arc visibly draining.
- The ramp is cancellable by re-touching (resumes from the current ramped value, not the old one).
- The mode is stated on screen as a chip on the loco card (`HOLD TO RUN`), because a throttle that stops when you let go is surprising if unannounced.
- Never enabled silently; never enabled by a server.

### 7.9 Releasing

Release is a three-step ritual because it is the only action that can strand a train:

1. `V0` and confirm the local value reached 0.
2. `M0-<addr><;>r`.
3. Only then clear the session and return to Roster.

If step 2 fails, the UI says `Released on this phone. The layout may still show it held.` — we do not claim a release we did not see.

### 7.10 Speed cap

A local, per-loco ceiling (10–100%, default 100%) shown as a tick mark on the arc. It exists because exhibitions hand a phone to a child and because a shunter should not do 100%. It never changes the loco's decoder limits; it is a UI-side clamp, stated as such in the sheet.

### 7.11 Server-side liveness (heartbeat)

- After `N`, the server replies with its E-stop timeout (`*<seconds>`). We send a heartbeat at half that interval, and never less often than every 5 s.
- We turn monitoring **on** (`*+`) as the default, because it is the only thing that stops a train when the phone is dropped, killed, or out of range.
- The trade-off is explicit: a Wi-Fi stall long enough to miss the window stops a train that was running perfectly. §6.4 exists to make that recoverable and explainable, and Settings offers an opt-out for operators on a shaky AP.

### 7.12 Forgiving reconnect

Reconnection is a **throttle behaviour**, not just a network concern, so it has its own rules:

- **The drive screen never unmounts.** The loco, the arc, and the Stop control stay exactly where they were, so the operator's hand does not have to find them again.
- **Frozen, not broken.** Input is *blocked*, not removed: the control stays visible and desaturated, so the shape of the screen is unchanged when it comes back.
- **Zero taps by default.** Auto-retry starts immediately; `Retry now` exists only for impatience, and is never required.
- **Same loco, zero speed.** We re-acquire the address the operator was holding and restore speed to 0. We never resume the last value — the train has been stopped by the layout, and a surprise restart is the one thing worse than a stop.
- **Say what the layout did.** If our heartbeat lapsed, the server stopped the train. That is stated, once, briefly, and never used to blame the network or the operator.
- **Time-box the drama.** Inline card for the first 30 s; only then does `Back to layouts` appear. An exhibition audience should never see an error dialog because of a 3-second blip.

---

## 8. Adaptive layout

Based on window size classes (`WindowSizeClass` via `material3-adaptive`) and posture.

| Class | Width | Layout |
|---|---|---|
| **Compact** | < 600 dp | Single column. `ShortNavigationBar`. Throttle centred at ~62% height. Sheets full-width, 60% detent. |
| **Medium** | 600–839 dp | Same single column but a wider arc (max 320 dp) and a 2-column function grid; nav bar, not rail, unless height is compact. |
| **Expanded** | ≥ 840 dp | Navigation rail + two-pane `ListDetailPaneScaffold`: Drive as the main pane, Roster as the supporting pane. Function grid becomes a permanent side panel when width ≥ 1000 dp. |

**Height matters more than width here.**

| Height | Behaviour |
|---|---|
| **Compact height** (< 480 dp, i.e. phone landscape) | Top bar collapses to a single 48 dp row; the loco card merges into the top bar; functions move to the right pane; Stop/Direction anchor bottom-right. |
| **Medium/Expanded height** | Full composition as drawn. |

**Posture**
- **Tabletop** (hinged/folded): throttle goes in the lower pane, status in the upper. This is also the right behaviour for a phone propped against a controller box, which is how people actually run trains.
- **Book** (foldable open): treat as medium width, two-pane only in Drive.

**Hard rules**
- No layout removes Stop or Direction.
- No layout requires two hands.
- No layout shows more than four persistent facts on Drive.
- Multi-window/freeform: the app is usable at 320 × 400 dp; below that, functions are sheet-only and the top bar drops the server name.

---

## 9. Typography and colour roles

| Element | Role |
|---|---|
| Speed value | `displayLarge` **emphasized**, auto-sizing 40–96 sp |
| Speed unit | `titleMedium`, muted |
| Loco address | `titleLarge` emphasized, tabular figures |
| Loco name | `bodyLarge` |
| Direction | `labelLarge`, uppercase, letter-spaced |
| Status | `labelMedium` + icon |
| Section headers | `titleSmall` emphasized |
| Body / helper | `bodyMedium` / `bodySmall` |

Colour: Material You dynamic colour by default, with a hand-tuned fallback scheme. Semantic colours are fixed and never dynamic: `error` for destructive and E-stop, `tertiary` for "held elsewhere", amber-family for power. Contrast is validated at theme generation; if the generated scheme fails 4.5:1 for body text or 3:1 for large text and icons, we fall back to the baseline scheme for that device.

---

## 10. Accessibility principles

### 10.1 Targets
- **48 dp minimum**, 8 dp minimum spacing.
- **72 dp** for Stop, **64 dp** for Direction and nav items, **56 dp** for function keys.
- Hit areas are never extended into a neighbouring control; the dial's gesture region is bounded and does not overlap Stop.

### 10.2 TalkBack semantics

| Element | Semantics |
|---|---|
| Speed arc | One node, not a slider *role* (Compose `Role` has no slider value). Progress semantics instead: `progressBarRangeInfo(0f..1f)`, `setProgress { }`, `stateDescription = "84 percent, forward"`, plus custom actions: `Stop`, `Half speed`, `Set 25%`, `Set 50%`, `Set 75%`, `Increase 5%`, `Decrease 5%` |
| Stop | Button, label `Stop train`, `stateDescription` includes current speed so it is meaningful out of context |
| Direction | Toggleable, `stateDescription = "Forward"` / `"Reverse"`, hint when disabled: `Available when stopped` |
| Status dot | Single node: `Connected to Junction Yard`, `Reconnecting, attempt 3`, or `Connection lost` |
| Functions | Grid semantics: `TalkBack` announces `Function 3, Whistle, on`; the grid is a collection, not 29 loose buttons |
| Power banner | `liveRegion = Polite` |

**Announcement discipline:** speed is *not* announced continuously. Announcements fire on discrete user actions (custom actions, drag end) and on ≥ 10% changes, debounced to 800 ms. A screen reader that reads "84, 85, 86" while you drive is unusable and drowns out the important events (link loss, steal prompt).

Decorative graphics (arc stroke, status dot fill, cap tick) are `clearAndSetSemantics {}` so they do not create duplicate nodes.

### 10.3 200% font scale
- Speed readout auto-sizes (`TextAutoSize`) between 40 and 96 sp and never clips. The arc grows to 320 dp and stops; beyond that the number scales down inside its range rather than clipping or overflowing.
- Top bar: the loco address never truncates; the server name ellipsizes first; the status word may drop to icon + content description at compact width.
- Function grid drops from 4 to 3 columns before it drops below 56 dp per cell.
- Nothing has a fixed height that contains text. No `maxLines = 1` on anything that carries meaning.
- Minimum rendered text size 12 sp; helper text at 200% may reflow, never overlap.

### 10.4 Reduced motion
- Follows the system animator duration scale by default. When motion is reduced (or the in-app override is on): springs collapse to instant, the connect→drive shared-element morph becomes a fade, shape morphs on press are disabled, and the shape-morph spinner is replaced by a plain indeterminate ring next to a static `Reconnecting` label (it still moves, because it conveys liveness, but without morphing or bounce).
- Haptics are **not** reduced by this setting; they are a separate, explicit toggle. Haptics are the accessible substitute for motion, not part of it.

### 10.5 Never colour alone
Every status pairs a colour with an icon **and** a word: `● Live`, `◌ Reconnecting`, `⚡ Power off`, `▲ Forward`. Function state uses container fill + `On` in the state description. Steal prompts use an icon + explicit consequence sentence.

### 10.6 Other
- No time-limited interactions. The steal prompt and the release sheet wait indefinitely.
- No drag-only path to any function: TalkBack custom actions and hardware keyboard arrow keys both drive the throttle (the dial is focusable and responds to `DPAD_UP`/`DPAD_DOWN` in 1% steps, `Shift` for 10%).
- Switch access: the throttle exposes `Increase`/`Decrease`/`Stop` as focusable actions.

---

## 11. Haptics and motion

### 11.1 Haptics

| Moment | Type | Notes |
|---|---|---|
| Speed detent | `SegmentTick` every 5% while dragging | Not per pixel; not per 1% (too buzzy) |
| At 0% and at cap | `SegmentTick`, slightly stronger | Confirms the limit without a visual check |
| Acquire granted | `Confirm` (single) | The only success celebration |
| Direction change committed | `Confirm` | |
| Stop pressed | `Confirm` | |
| Steal prompt shown | `Reject` | Signals "you can't just do this" |
| Reconnect success | `SegmentTick` | Quiet; a burst would startle a quiet exhibition hall |
| Release complete | `SegmentTick` | |
| Connection lost | **None** | Startling, and it is not the operator's fault |

- Gated by the system haptic setting *and* the in-app toggle; never the only feedback channel.
- No haptics on scroll, sheet detents, or nav changes.

### 11.2 Motion

Global scheme: `MaterialExpressiveTheme` with `MotionScheme.expressive()` for entry, hero, and container morphs. A scoped override to `MotionScheme.standard()` covers the throttle, the speed number, and the arc, where overshoot would be a lie.

| Element | Motion |
|---|---|
| App launch → Connect | Fade-through, 200 ms |
| Connect → Drive | Shared-element: the server row expands into the top bar; 400 ms expressive spatial spring |
| Speed arc fill | 120 ms linear interpolation, no spring, no overshoot |
| Speed number | Never animated: instant on drag and on Stop; only the arc eases |
| Press feedback | Shape morph (expressive default) on buttons, 90 ms |
| Reconnect overlay | Fade + 0.98 scale in, 200 ms; out on success, 150 ms |
| Sheet | Standard M3 sheet motion; no bounce |
| Loading | `LoadingIndicator` (shape-morph sequence) for connect; never `WavyLinearProgressIndicator` on Drive |
| Screen transitions (nav) | 300 ms fade-through, no lateral slide — lateral motion implies hierarchy the app does not have |

**Rule:** motion never delays input. A spring in flight is cancelled by a touch, and no animation gate blocks a command.

---

## 12. Content and tone

- **Second person, present tense, no jargon on the surface.** "Track power is off", not "PPA=0".
- **Say what happened and what to do next**, in that order, in two lines maximum.
- **Never blame.** "Nothing answered at that address" — not "invalid host".
- **No exclamation marks. No "Oops".** This is a control surface, not a companion app.
- **Protocol vocabulary is only in Diagnostics.** No "throttle ID", "steal", "slot", or "consist" on the surface; the UI says "in use by another throttle" and "take control".
- **Numbers are tabular and never abbreviated** (`3412`, not `3.4k`).

Glossary for reference (surface ↔ protocol):

| Surface | Wire |
|---|---|
| Live | socket open, heartbeat acking |
| In use by another throttle | `MTS` steal prompt / `HM` address-in-use |
| Track power off | `PPA0` |
| Needs a newer server | `VN` < 2.0 |
| Take control | `MTS` steal request |
| Release | `M0-<addr><;>r` |

---

## 13. Design tokens (summary)

| Token | Value |
|---|---|
| `touch.min` | 48 dp |
| `touch.primary` | 72 dp (Stop), 64 dp (Direction), 56 dp (functions) |
| `space.grid` | 4 dp base; 8 / 12 / 16 / 24 / 32 |
| `radius.container` | M3 shape scale; no custom radii above 28 dp on Drive |
| `arc.size` | 232 dp compact, 320 dp max |
| `motion.scale.throttle` | standard scheme, damping 1.0 |
| `motion.duration.ui` | 200–400 ms |
| `motion.duration.throttle` | 0 ms (number), 120 ms (arc) |
| `haptic.detent` | 5% |
| `cmd.rate` | 10 Hz coalesced |
| `reconnect.backoff` | 0.5 / 1 / 2 / 4 / 8 s, cap 8 s |
| `stale.threshold` | 2 × heartbeat interval |

---

## 14. Acceptance criteria for Step 0

This spec is considered ready for Step 1 when:

1. Every state in §6 has copy, an action set, and a named UI state in the drive reducer.
2. The drive screen has been reviewed at 320 dp, 360 dp, 600 dp, 840 dp, and 1280 dp widths, portrait and landscape, light and dark, at 100% and 200% font.
3. Stop is reachable one-handed in all five layouts, verified with the reach overlay.
4. TalkBack can acquire, drive to 50%, change direction at stop, stop, and release without touching the screen visually.
5. The reconnect overlay has been exercised with forced link loss at 0%, mid-drag, and with a sheet open.

---

## References

- Material 3 Expressive — <https://m3.material.io/> · motion physics: <https://m3.material.io/blog/m3-expressive-motion-theming>
- M3 touch targets: <https://m3.material.io/foundations/designing/structure>
- Compose Material 3 releases (expressive components, `MaterialExpressiveTheme`, `MotionScheme`): <https://developer.android.com/jetpack/androidx/releases/compose-material3>
- Expressive research summary (4× faster element location): <https://design.google/library/expressive-material-design-google-research>
- JMRI WiThrottle protocol: <https://www.jmri.org/help/en/package/jmri/jmrit/withrottle/Protocol.shtml>
- Android window size classes / adaptive layouts: <https://developer.android.com/develop/ui/compose/layouts/adaptive>
