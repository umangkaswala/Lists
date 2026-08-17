# Progress

> Read this file first in every new session — it says what's done and what
> to do next. Full phase descriptions are in [PLAN.md](PLAN.md); working
> conventions and environment setup are in [CLAUDE.md](CLAUDE.md).

**Current status: Phase 1 complete and pushed. Start Phase 2 next.**

## Phase 0 — Project bootstrap — ✅ DONE (2026-08-17, commit `6b580be`)

Built: Compose Android project skeleton, package `com.stackpointer.lists`,
exact light/dark M3 Expressive color/type/shape tokens from the design in
`ui/theme/`, empty `NavHost` with a placeholder Home screen.

Verified: built via `gradlew assembleDebug`, installed on the `Pixel_9`
emulator, launched, screenshotted — confirmed theme colors/type/shapes
render correctly against the design's exact hex values, no crash in logcat.

Known simplifications (intentional, not bugs):
- Font is `FontFamily.Default` (system Roboto), not the design's Roboto
  Flex — deferred to Phase 9 polish to avoid downloadable-font setup risk
  this early.
- ~9 Material 3 color roles the design didn't specify explicitly (secondary,
  surfaceVariant, inverse*, surfaceTint, scrim, surfaceBright/Dim, onError)
  are filled with standard M3-convention approximations, documented in
  `ui/theme/Color.kt`'s header comment.

## Phase 1 — Data layer, Home, Lists, Reminder Detail — ✅ DONE (2026-08-17, commit `9d8034c`)

Built:
- Room database (`data/db/ListsDatabase.kt`) with `ReminderEntity` (soft
  delete via `deletedAt`), `ReminderListEntity`, `ChecklistItemEntity`, and a
  schema-only `PlaceEntity` for Phase 7. Seeded with sample reminders/lists
  on first launch.
- Repositories (`ReminderRepository`, `ListRepository`) and manual DI via
  `AppContainer`/`ListsApplication`.
- **Home screen**: Today tile with live overdue/to-go counts, per-list
  tiles + filter chips, grouped sections (Overdue/Today/Upcoming), reminder
  cards with working complete-toggle and star-important-toggle, capture pill
  (visual only — Phase 2 wires it up).
- **Lists screen**: create (name + color picker), drag-to-reorder, delete
  (with a confirmation dialog warning that the list's reminders go too),
  stubbed "Shared with you" card.
- **Reminder Detail screen**: property card (due/list/repeat), note card,
  history card (stub copy until repeats exist), bottom bar with a working
  Complete button and stubbed Edit/Snooze/Share/Delete (show a "coming in a
  later phase" snackbar).

Verified end-to-end on the `Pixel_9` emulator (not just compiled): browsed
Home, toggled star and complete (confirmed the change persists across
navigating away and back — proof Room persistence actually works), created
a list with a custom color, deleted it with the confirm dialog, opened a
reminder's Detail screen and read back its due date/list/note correctly.

Bug found and fixed during this verification pass: the custom top bars on
Home/Lists/Detail were drawn under the system status bar (an
`enableEdgeToEdge()` consequence), which made the top-right buttons
genuinely untappable in roughly their top 40px, not just visually cramped.
Fixed with explicit `statusBarsPadding()`/`systemBarsPadding()` — see
CLAUDE.md's gotchas list, since this will recur on new screens if forgotten.

Not yet manually verified: dragging a list row to reorder it (create/delete
were thoroughly tested; the drag gesture itself wasn't exercised through
automation — worth a manual check next time the Lists screen is open).

## Phase 2 — Capture create/edit + Capture-When — ⬜ NOT STARTED (next up)

Per PLAN.md: bottom-sheet Capture with plain text entry, working When
sub-editor (date/time picker, hardcoded quick-time chips for now), wired
into the capture pill, the Home empty-state prompts, and Detail's Edit
action. Demo target: create and edit a reminder with a due date/time
end-to-end through the actual Capture UI (not just seeded data).

## Phases 3–12 — ⬜ NOT STARTED

See PLAN.md for full descriptions of each.

## Open product/technical TODOs carried from PLAN.md

- Recycle-bin retention length: defaulted to 30 days, not user-specified.
- Real app icon: still a placeholder.
- Voice recognition privacy disclosure wording: needed by Phase 8/9, not
  written yet.
