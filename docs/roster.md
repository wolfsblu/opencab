# Roster and acquisition

**Status:** Implemented in `:feature:roster` (Step 8)
**Scope:** Local locomotive roster, DCC address entry, the Drive picker, the roster editor, one-active-
loco acquisition, and optional JMRI roster import.

## 1. Why this shape

The roster is the first place where the app stores operator intent instead of observing a live
layout. The design keeps that state behind `RosterRepository` in `:core:data`, with plain domain
types in `:core:model`, so neither Compose nor the protocol layer owns it.

Two rules drive the implementation:

1. **A DCC address is not an opaque integer.** Short and long addresses overlap at 1–127 and the
   difference is sent on the wire. The roster stores the type explicitly and the entry layer flags
   the overlap instead of guessing silently.
2. **History is not rewritten.** Editing a locomotive's name, favourite, colour, icon, or function
   labels updates the row. Changing its address appends a new row and leaves the previous one in
   place, so recent-use history and any other reference to the old entry remain meaningful.

## 2. Persistence

`roster_loco` replaces the unused `saved_loco` scaffold.

| Column | Meaning |
| --- | --- |
| `id` | Stable local UUID. |
| `name` | Display name. |
| `address` + `addressType` | DCC number and `SHORT`/`LONG`. A unique index prevents duplicates. |
| `functionLabels` | Operator-local labels, encoded as sparse `number=label` lines. |
| `serverFunctionLabels` | Labels last sent by the layout, kept separately so a local override survives. |
| `isFavorite` | Operator favourite, not a layout concept. |
| `colorKey` + `iconKey` | Named palette and silhouette keys, so the artwork can change without a migration. |
| `lastUsedEpochMillis` | Updated whenever the operator acquires the locomotive. |
| `serverRosterId` | JMRI roster id when the entry came from the layout roster. |
| `createdAtEpochMillis` | Local creation timestamp. |

`FunctionLabelCodec` is deliberately a tiny line format rather than JSON: labels are sparse,
single-line text, and the persistence layer should not pull a serialization dependency for them.

Changing an address clears `serverRosterId` on the new entry. The old layout id still points at the
old address; acquiring the corrected entry must use the direct DCC address instead.

### Migration

Version 2 adds `roster_loco`, creates the unique address index, copies scaffold rows whose
`roadNumber` is a valid whole-number DCC address, and drops `saved_loco`. Invalid or duplicate
scaffold rows cannot be assigned a meaningful address and are discarded; the scaffold was never
exposed in the UI. `RosterMigrationTest` creates a real v1 database, runs the migration through
Room, and asserts both the carried-forward row and the removed table.

## 3. Address validation

`RosterAddressRules` is the single source of truth:

- Short addresses: 1–127.
- Long addresses: 1–10239.
- A number without an explicit type inside 1–127 defaults to **short** and is marked ambiguous.
- An explicit long address inside 1–127 is valid and stays ambiguous.
- An explicit short address above 127 is an error with `LONG` as the suggested type.
- Zero, negative, non-numeric, and above-10239 inputs have distinct errors.

The picker and editor both offer **Auto**, **Short**, and **Long** chips. Auto is the default for a
new locomotive, so typing `42` produces `S42` and a visible overlap warning, while typing `3412`
produces `L3412`. The operator can always force the other type.

## 4. Duplicate prevention

Duplicate prevention has three layers:

1. `RosterRepository.create` checks the address before inserting and returns
   `RosterSaveResult.DuplicateAddress` containing the existing entry. The UI shows the existing
   name and offers “Use existing”.
2. The `(address, addressType)` unique index is the database backstop against a race or a bad
   caller.
3. `ThrottleSession.acquirePrimary` is keyed by address. Selecting the already-requested or
   already-acquired locomotive returns `ALREADY_ACTIVE` without creating a second slot or sending
   another acquire. Selecting a different locomotive releases the previous desired slot first.

Server-side conflicts remain visible on Drive: a `StealRequested` event becomes the existing
“Locomotive in use” card with **Take control**, so the operator makes the decision explicitly.

## 5. Picker and empty states

`LocoPickerSheet` is a Material 3 modal sheet owned by `:feature:roster` and hosted by `:app` over
Drive. It does not require Drive to depend on the roster feature.

- Search matches the display name, the address digits, and `S3`/`L3412` forms.
- Without a query it groups **Favorites**, **Recent**, and **All locomotives**. A locomotive appears
  in at most one group.
- “Add address” is always available. The add form creates the entry and acquires it immediately.
- When the roster is empty, the sheet is an empty state whose primary action opens the add form.
- The form carries a blank name into `Loco <address>`; no unnamed row is persisted.

`RosterScreen` shows the same list with a play action per row. Its empty state leads directly to
the editor, and an import banner appears when the layout advertises a roster.

## 6. Function labels

Server labels arrive on acquisition in the `M<key>L…` event and flow through the throttle engine.
Drive renders local labels first, then server labels, then `F<n>`. `DriveViewModel` also copies the
layout's labels back to the matching roster row, but never into the local column. The editor shows
the server label as a field placeholder, so clearing a local override falls back to the layout's
text instead of losing it.

## 7. JMRI roster import

The protocol layer already decodes `RL` into `WiThrottleEvent.RosterList` and the engine exposes it
as `ThrottleUiState.serverRoster`. Import is behind
`RosterFeatureFlags.JMRI_ROSTER_IMPORT_ENABLED` (default on). When enabled and the layout has
entries, Roster shows “Import N from layout”. Import adds only missing addresses and sets
`serverRosterId`; the roster id is reused for the `E<id>` acquire form. Duplicate addresses are
skipped, and the rest of the roster works unchanged if the flag is disabled or the layout never
sends a roster. Set the constant to `false` to remove the import affordance entirely.

JMRI’s first `RL` field is its roster entry id, not a human name (recorded in
[`protocol.md`](protocol.md#9-known-quirks)). Import uses it for both the display name and the
acquire id; the operator can rename the entry locally without losing the id.

## 8. Acquisition flow

```text
Drive / picker / roster
        │
        ├── picker selects an existing entry ──┐
        ├── picker adds an address ───────────┤
        └── roster row play action ───────────┤
                                              ▼
                                 ThrottleSession.acquirePrimary
                                  ├─ same active address → no-op
                                  ├─ release other desired slot
                                  └─ controller.acquire(address, name, serverRosterId)
                                              │
                                              ▼
                                  Drive maps the last desired slot
```

Roster and picker view models depend on `ThrottleUiSource` for observation and a small
`LocoAcquirer` port for the action. They never see sockets, commands, or protocol types.

## 9. Tests

| Area | Test |
| --- | --- |
| Address rules, overlap, ranges, labels | `core/model/src/test/.../RosterAddressRulesTest.kt` |
| Label codec | `core/data/src/test/.../FunctionLabelCodecTest.kt` |
| Room DAO and unique index | `core/data/src/test/.../RosterLocoDaoTest.kt` |
| v1 → v2 migration | `core/data/src/test/.../RosterMigrationTest.kt` |
| Repository persistence, duplicate, address change, import | `core/data/src/test/.../DefaultRosterRepositoryTest.kt` |
| Duplicate primary acquisition, replacing a slot | `core/data/src/test/.../ThrottleSessionTest.kt` |
| Picker filtering and grouping | `feature/roster/src/test/.../RosterSearchTest.kt` |
| Picker add/duplicate/use-existing/select | `feature/roster/src/test/.../LocoPickerViewModelTest.kt` |
| Editor validation, address change, labels | `feature/roster/src/test/.../LocoEditorViewModelTest.kt` |
| Roster row mapping, drive, import | `feature/roster/src/test/.../RosterViewModelTest.kt` |
| Drive local-label override | `feature/throttle/src/test/.../DriveStateMappingTest.kt` |

Run them with:

```bash
./gradlew :core:model:test :core:data:testDebugUnitTest :feature:roster:testDebugUnitTest
```

## 10. Known limits

- v1 intentionally drives one locomotive. The engine supports more slots; the roster releases the
  previous desired slot before acquiring a new one.
- Track power remains read-only.
- Import depends on the layout sending `RL`. If a JMRI build does not advertise its roster, the
  local roster and manual add flow are unaffected.
- There is no delete action in the editor yet. Rows are append-only when an address changes, which
  is the point of the history rule; a future cleanup action should be deliberate and confirmed.
