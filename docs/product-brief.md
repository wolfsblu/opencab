# OpenCab — Product Brief

**Status:** Draft v1 (Step 0 deliverable) · **Owner:** Product + Android · **Date:** 2026-11-20
**Companion docs:** [`ux/ux-spec.md`](ux/ux-spec.md) · [`adr/0001-platform-and-stack.md`](adr/0001-platform-and-stack.md)

---

## 1. One-liner

OpenCab is an Android cab for DCC model railways: **pick up a locomotive and drive it, one-handed, in under twenty seconds, on a layout you have never used before.**

## 2. The problem

People who run model trains already have working control. What they do not have is a *phone-shaped* control that respects the fact that they are looking at a train, not at a screen.

The two incumbents set the bar and define the gap:

| Option | Strength | What it costs the operator |
|---|---|---|
| **EngineDriver** (WiThrottle client) | Complete, battle-tested, speaks every corner of the protocol | Dense preference surfaces, terminal-era visual language, many screens deep before a train moves |
| **Vendor handset apps** | Matched to hardware | Locked to one command station; little portability across layouts |

The three failure modes we design against, all observed in real operation:

1. **The "where am I" problem.** The operator looks down, sees a wall of numbers and settings, and has to re-read the screen to answer: *which loco, which direction, how fast, am I even connected?*
2. **The "who has 3412" problem.** On a shared club layout, two people acquire the same address. One silently steals it; the other's train stops mid-run and nobody knows why.
3. **The "it dropped and I didn't notice" problem.** Wi-Fi blips, the heartbeat lapses, the command station emergency-stops the loco — but the app still shows 62% and a green dot. The operator keeps turning a knob that is no longer connected to anything.

Everything below follows from those three.

## 3. Target users

| Persona | Real context | What they need | What breaks today |
|---|---|---|---|
| **Solo home operator** — *Mira* | Basement or spare room, one JMRI instance on a Raspberry Pi, 4–8 locos, phone in one hand, track plan in the other. Wi-Fi is one consumer AP through a floor. | A throttle that survives a Wi-Fi hiccup and gets back to the same loco without re-navigating. | Drops force a full reconnect + re-acquire manual sequence. |
| **Club night operator** — *Dev* | 6–12 operators, one shared layout, one WiThrottle server, everyone on their own phone, ambient noise, standing, sometimes cold hands or gloves. Handovers every few minutes. | Unambiguous acquisition, honest "in use" feedback, three-second handover. | Silent steals; no reliable signal that an address is taken until it is taken. |
| **Exhibition operator** — *Ruth* | All day, public, dim hall with hard glare, phone passed to strangers, battery anxiety, an audience watching. | A mode where a stranger cannot break anything, and an instant, obvious stop. | Settings and function grids are one mis-tap away; a dropped link looks like a dead app in front of a crowd. |

**Anti-persona:** the layout builder who wants to configure JMRI, tune CVs, or edit the track plan. They are served by JMRI's own tools. We are the cab, not the workshop.

## 4. Jobs to be done

> *When I am standing at a layout with a train in front of me, I want to …*

| # | Job | Rank | Why this rank |
|---|---|---|---|
| **J1** | …take control of a locomotive and set it moving, without configuring anything. | **1** | Nothing else matters if this is slow. It is the whole product. |
| **J2** | …stop the train immediately and know that it stopped. | **2** | Highest consequence per occurrence. Used many times per session. Must never be ambiguous. |
| **J3** | …recover from a dropped connection back to the same loco, without losing my place. | **3** | Frequency is low, blast radius is high; it is the failure that erodes trust fastest. |
| **J4** | …find and join the layout's server, including on a network with no discovery. | **4** | Once per session, but blocking: a failed connect is a failed session. |
| **J5** | …hand a loco over, or take a different one, without two people fighting over an address. | **5** | Very high at club nights and exhibitions; lower for the solo operator. |

### Top 5 tasks (what we will build and measure)

1. **Acquire and move.** Launch → connected → loco acquired → speed > 0.
2. **Stop.** Bring the train to a standstill, and release the address when the session ends.
3. **Reconnect.** Detect loss, tell the truth about it, restore the session to the same loco.
4. **Connect.** Discover the server, or enter one by hand, and reach the roster.
5. **Hand over / switch.** Release cleanly; take another loco; take one that someone else holds, deliberately.

Everything not on this list is secondary — including functions, which are a convenience layered on J1, not a job of their own.

## 5. v1 scope

### In scope

**Connect**
- mDNS discovery of `_withrottle._tcp` (best-effort, with a visible timeout — never an infinite spinner).
- Manual `host:port` entry, first-class, not buried.
- "Recent layouts" list (local only), with one-tap reconnect.
- Protocol version check (`VN`), server identity (`HT`/`Ht`), and a compatibility report in diagnostics.
- Clear handling of: nothing found, wrong port, permission denied, version mismatch.

**Drive (home)**
- One active locomotive. Acquire, speed 0–126, direction, stop, release.
- Delta-based throttle that cannot jump. Hold-to-run (dead-man) as an option.
- Always-visible connection truth: live / reconnecting / lost, with last-known speed labelled as last-known.
- Local speed cap per loco (for handovers and beginners).
- Screen stays awake while a loco is held.
- Persistent stop control, always in the same place.

**Roster**
- Server roster (`RL`) with search, plus manual address entry (short/long) for locos not in the roster.
- Recents and local favourites.

**Functions**
- F0–F28 as reported by the server, using server labels, honouring momentary vs latching where the server tells us.

**Settings**
- Haptics on/off, speed display (% / steps), hold-to-run default, keep-screen-on, reduced-motion override, diagnostics log export, about.

**Status**
- Track power **read-only** (`PPA`). See non-goals for why.

### Explicit non-goals (v1)

| Not doing | Why |
|---|---|
| **CV programming / decoder configuration** | Different job, different user posture (seated, reading, careful). It would dominate the information architecture. |
| **Turnout, route, or layout control** | Explicitly out. A layout panel is a map problem, not a cab problem, and it competes for the same thumb real estate. |
| **Track power *control*** | Status only in v1. Power is a layout-wide, multi-operator action; a mis-tap stops everyone's train. Revisit when we can show who else is running. |
| **Accounts, cloud sync, sign-in** | The layout is the identity. Local state only. This also keeps crash reporting on Play vitals rather than an SDK. |
| **Multiple simultaneous locos** | One cab, one train. Switching is a first-class action; juggling is not. |
| **Consists / MU** | Real, useful, and a whole interaction model of its own. v1.1 at the earliest. |
| **iOS, desktop, web** | Android-first because the spare phones in a club bag are Android. Not a promise we can keep cheaply yet. |
| **Timetables, ops sessions, fast clock UI** | Ops tooling belongs on a tablet or a dispatcher's screen. |
| **Sound programming, function labelling** | Server-supplied labels are enough. |

## 6. Success criteria

| Criterion | Target | How measured |
|---|---|---|
| **Launch → train moving** (returning user, server present) | p50 ≤ 15 s, p90 ≤ 30 s | Instrumented debug builds; 10-run stopwatch protocol in usability tests |
| **Cold start → train moving** (new layout, manual entry) | p90 ≤ 60 s | Usability test, first-time participants, no help |
| **Reconnect success** | ≥ 95% of drops under 30 s recover automatically within 5 s, with zero taps; 100% recoverable within 10 s with one tap | Forced-drop harness (airplane mode / AP kill) across 200 runs |
| **Crash-free sessions** | ≥ 99.5% | Play Console vitals (no in-app SDK, no account) |
| **ANR rate** | ≤ 0.10% | Play Console vitals |
| **One-handed operation** | 100% of Drive-screen primary actions reachable in the thumb arc, no two-finger gesture, no long-press required for a core action | Reach-envelope overlay in design review + 12-participant one-handed test |
| **Throttle safety** | 0 unintended speed changes > 20% across instrumented drag sessions; 0 in usability testing | Automated gesture fuzzing + 12-participant test |
| **Handover** | Stop + release + next loco acquired in ≤ 3 s | Timed task in usability test |
| **Truthfulness** | Zero sessions where the UI shows a live speed while the link is down | Assertion in state-reducer tests; every link-state transition covered |

Two of these are worth more than the rest. **Truthfulness** is a correctness property, not a polish item: an app that lies about speed is worse than an app that is slow. **One-handed operation** is the difference between a phone app and a cab.

## 7. Product principles

1. **Eyes on the train, not on the screen.** If a fact is needed while moving, it must be readable at a glance from 60 cm in under a second.
2. **One thumb, always.** Portrait, landscape, phone, tablet. The right hand does not need a second hand.
3. **Never lie about state.** Stale data is labelled stale. Unknown is unknown. "In use" is never guessed.
4. **Stopping is always one tap.** No confirmation, no hold, no menu, no small target.
5. **Reconnection is a state, not an error.** A blip is expected weather, not a failure screen.

## 8. Assumptions (stated, not asked)

These were decided rather than raised as blocking questions, because each has a defensible default and none blocks Step 1.

| # | Assumption | Reversal cost |
|---|---|---|
| A1 | Product name **OpenCab**; application ID `dev.opencab` (placeholder, cheap to change before first release). | Low |
| A2 | **Android only**, minSdk 26, targetSdk 36. Club spare phones skew old. | High (see ADR-0001 rejected alternatives) |
| A3 | **WiThrottle is the only protocol in v1**, via JMRI. Digitrax LNWI and MRC speak the same dialect with quirks; we treat them as "WiThrottle-family", feature-detected. | Medium |
| A4 | **No backend.** No accounts, no sync, no telemetry SDK. Metrics come from Play vitals and instrumented tests. | Low |
| A5 | **Track power is read-only** in v1 (see non-goals). | Low |
| A6 | **One active loco** per session; switching releases the current one first. | Medium |
| A7 | **Speed is shown as a percentage** of the 126-step range by default; raw steps and scale-speed calibration are diagnostics/settings later. | Low |
| A8 | Layout operators are **not** adversarial, but strangers at an exhibition are careless. Guard rails protect against accident, not malice. | Low |
| A9 | The JMRI server is on the same L2 network. No NAT traversal, no remote operation. | Low |

## 9. Open questions (non-blocking, decide by Step 2)

1. **Server-side heartbeat.** Enabling `*+` makes JMRI E-stop the loco if our heartbeat lapses — a genuine safety win, at the cost of more disruptive drops on flaky Wi-Fi. Default: **on**, with a Settings escape hatch. Confirm with club operators.
2. **Steal policy.** Default is "never auto-steal, always ask". Should a club be able to pre-authorise stealing for known addresses, or is a confirm always required?
3. **Exhibition hand-over mode** (hides settings/functions, forces hold-to-run, caps speed). Cheap and high-value; is it v1 or v1.1?
4. **Scale speed.** Percent is honest but not immersive. Do operators want mph/km/h with a per-loco calibration, or is that a toy?
5. **Function coverage.** F0–F28 is protocol-complete but most operators use 6 keys. Ship all, or curate?

## 10. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| **Android mDNS is unreliable** (JMRI docs already say discovery often fails) | Connect gate blocks J1 | Manual entry is first-class, not a fallback; recents make the common case one tap |
| **Android 16+ local-network permission** gates LAN sockets as well as discovery | Silent connect failures on new devices | Ask at the right moment, explain in product language, deep-link to settings, never loop the permission prompt |
| **WiThrottle dialect drift** (JMRI vs LNWI vs MRC) | Weird failures on real layouts | Version gate at `VN`, feature-detect, never assume; diagnostics log for field reports |
| **Compile-time churn in M3 Expressive APIs** | Build breaks mid-development | All expressive components behind `:core:ui`; pinned BOM; screenshot tests (see ADR) |
| **Phone thermals/battery at all-day exhibitions** | Dead device at 3 pm | Keep-screen-on only while driving, dark surfaces, no continuous animation, no wavy progress on the drive screen |
| **The throttle feels laggy** | Operators over-correct and the train oscillates | Latency budget (UX spec §7.6), optimistic UI, 10 Hz coalesced command rate, always flush the final value |
