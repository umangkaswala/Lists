# Progress

> Read this file first in every new session — it says what's done and what
> to do next. Full phase descriptions are in [PLAN.md](PLAN.md); working
> conventions and environment setup are in [CLAUDE.md](CLAUDE.md).

**Current status: Phase 4 complete and pushed. Start Phase 5 next.**

**Phase 5 is one of the four physical-device-critical phases** (5, 7, 10, 11)
and PLAN.md/CLAUDE.md both call for running the **Plan agent** before starting
it. Notifications and exact alarms behave very differently on an emulator, so
this is the first phase that genuinely needs Umang's own phone to verify.

**Process note (2026-08-17):** from Phase 2 onward, every phase gets a
`/code-review` pass on the diff after self-verification and before
committing — see CLAUDE.md's "Per-phase workflow" section. Phases 0 and 1
below predate this and were not code-reviewed.

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

## Phase 2 — Capture create/edit + Capture-When — ✅ DONE (2026-08-17, commit `44387b6`)

Built:
- **Capture bottom sheet** (`capture/CaptureSheet.kt`, `CaptureViewModel.kt`,
  `CaptureTarget.kt`): free-text title field, action-row icons (When wired;
  Where/Checklist/Photo/List show a "coming in a later phase" snackbar),
  Send button that creates or updates a reminder depending on whether it was
  opened for a new reminder or to edit an existing one.
- **When sub-editor**: all-day switch, four hardcoded quick-time preset
  chips (Later today / Tonight / Tomorrow / Next week — becomes
  Settings-driven in Phase 9), Repeat/Early alert/Alert style stub rows.
  Picking a preset returns to the typing view with a due-date chip (tap to
  reopen When, X to clear).
- Wired into: the Home capture pill, the two Home empty-state example-prompt
  buttons (added this phase — Phase 1 had left them out), and Detail's Edit
  button (previously stubbed).
- `ReminderRepository.updateReminderFields()` added for the edit path.

Verified end-to-end on the `Pixel_9` emulator: created a reminder through
the real Capture UI with a due date picked via the When quick-picks
(confirmed it appeared correctly grouped on Home); opened that reminder's
Detail screen, tapped Edit, confirmed the sheet pre-filled with its existing
title/due date, changed the title, saved, confirmed Detail reflected the
change; confirmed a stub action (Where) shows its snackbar.

**Ran `/code-review` on the diff** (first phase to do so) and it found three
real bugs, all fixed before committing:
1. Opening the Capture sheet twice in a row (e.g. save one reminder, then
   tap Edit on another) silently reused the *first* sheet's already-saved
   state, causing the second sheet to instantly self-dismiss with no
   feedback. Root cause: `androidx.lifecycle.viewmodel.compose.viewModel()`
   caches by call-site + class, and `CaptureTarget.New()` instances are
   structurally equal to each other, so Compose treated repeat opens as "the
   same" request. **I also independently found and fixed** a related bug
   during my own manual testing before the code-review ran: this same
   stale-state issue made a star-toggle appear to silently revert (it was
   actually a mistapped test coordinate, but chasing it surfaced the deeper
   pattern the review then confirmed).
2. As a consequence of fixing #1 with a naive "always-fresh key" counter,
   `/code-review` caught that every sheet-open was leaking a retained
   ViewModel for the life of the app process (nothing ever cleared the old
   keyed entries). Fixed properly by not using `androidx.lifecycle.ViewModel`
   for `CaptureViewModel` at all — it's now a plain class created with
   `remember(sheetKey)`, so it's garbage-collected like any other Compose
   state when the sheet closes.
3. Saving a reminder called `onDismiss()` directly from the composable body
   instead of from a `LaunchedEffect`, which skipped the bottom sheet's
   normal hide animation. Fixed.

Also found and fixed during my own manual testing (before code-review): a
`ModalBottomSheet`'s content renders in its own popup window, so the
`SnackbarHost` hosted at the NavHost level was completely invisible while
the sheet was open — tapping a stub action (e.g. "Where") produced no
visible feedback at all. Fixed by giving the Capture sheet its own
`SnackbarHost` inside its own window layer.

Not yet built (intentionally, deferred to later phases per PLAN.md):
natural-language parsing of typed text into chips (Phase 3),
Where/Checklist/Photo/List actions (Phases 4/7/8), Repeat/Early alert/Alert
style (Phase 3/9).

**Scope simplification worth flagging (my call, not pre-approved in
PLAN.md):** the When editor only offers the four quick-time presets — there
is no calendar/clock picker to set an arbitrary date or time. PLAN.md's
Phase 2 line says "date/time, hardcoded quick-time chips for now," which
could reasonably be read either way; I read it as "chips only" to keep this
phase's scope contained, but a real date/time picker may be worth pulling
forward if presets feel too limiting in practice. Flagging for the user
rather than silently deciding it's fine.

## Phase 3 — Parser + RRULE engine + Custom Repeat + widget spike — ✅ DONE (2026-08-17)

First phase where work was **split across parallel Sonnet subagents** (at the
user's request) rather than written start-to-finish in the main session. What
that looked like: I fixed the shared `RRule` data contract first, then handed
out three independent, individually-testable pieces — the recurrence engine,
the text parser, and the Glance widget spike — and built the Repeat editor and
all the app wiring myself while they ran. Notes on how that went at the bottom.

Built:
- **`recurrence/`** — an RFC 5545 subset engine: `RRule` (model +
  serialize/parse, deliberately narrow — no BYSETPOS/BYWEEKNO/positional
  BYDAY), `RRuleExpander` (`occurrencesFrom`, `nextAfter`), `RRuleText`
  (`rruleSummary` for the Repeat banner and Detail row, `rruleShortLabel` for
  the Capture chip). **49 unit tests**, including the RFC "skip don't clamp"
  month-end rule (monthly from 31 Jan → 31 Mar, never 28 Feb), leap-day yearly,
  DST stability across the spring-forward date, and UNTIL/COUNT boundaries.
- **`parser/`** — `CaptureParser.parse(text, now)`, pure on-device string/regex
  work, no network or ML. Handles dates (today/tonight/tomorrow/weekday
  names/`on 25 Dec`/`in 3 days`…), times (`7pm`, `19:30`, noon, morning…) and
  repeats (`every Tuesday`, `every weekday`, `every other week`, `every 15th`…),
  longest-match-wins with word-boundary matching, and returns a `cleanedTitle`
  with the recognised words stripped. **26 unit tests**, including negatives
  ("Satisfied" must not parse `sat`, "market" must not parse `mar`).
  `now` is injected, never read from the clock inside the parser, so the tests
  are deterministic.
- **`repeat/RepeatEditor.kt`** — design screen S10: summary banner, frequency
  segments, "every N weeks" stepper, day-of-week toggles, and Never / On a date
  (real M3 date picker) / After N times endings, plus a "Don't repeat" escape.
  Written as a **value-in/value-out composable**, not a screen with its own
  ViewModel or nav route, so it can move from "sub-editor inside the Capture
  sheet" to "full-screen nav destination" later at no cost.
- **`widgets/HelloWidget`** — the deliberately tiny Glance smoke test. Its whole
  job was to prove the widget toolchain (Glance 1.1.1, manifest receiver,
  `res/xml` provider metadata, Compose interop) works *now* rather than
  discovering a problem in Phase 10/11. It compiles and is wired up.
- **Wiring**: parsed chips + the design's "Read from what you typed" hint in
  Capture; Repeat reachable from both the chip and the When editor; repeat
  shown on Detail and as a glyph on Home cards; `repeatRule` + a new
  `seriesStartAt` column persisted; a repeating "Bins out" sample seeded.
- **Completing a repeating reminder rolls it forward** to its next occurrence
  instead of striking it off, skipping every occurrence already missed, and
  only completes for real once the series ends.

Verified end-to-end on the `Pixel_9` emulator (not just compiled): typed the
design's own example "Bins out every Tuesday at 7pm" and watched it produce the
two chips the design shows; opened Repeat from the chip and built "every 2
weeks on Tuesday and Friday" (banner text matched the design's wording);
saved it; confirmed the reminder was stored with the date words stripped from
its title; **completed it and confirmed it moved from Tue 18 Aug to Fri 21 Aug**
— the correct next occurrence, which is the single most important behaviour in
this phase. Also checked "Standup every weekday at 9:30" (typed at 10:19am,
correctly landed on *tomorrow* 9:30, not today) and the delete-the-date-word
case. 75 unit tests pass on a clean build; no crashes in logcat throughout.

Bug found by my own testing before the review: bumping the Room schema version
wiped the sample data and never put it back, because destructive migration
doesn't call `onCreate`. Fixed; now in CLAUDE.md's gotchas.

**`/code-review` found nine issues, all real, all fixed before committing.**
The ones worth remembering:
1. Picking a repeat with no due date silently dropped the rule on save. Now the
   first occurrence is derived from the rule instead.
2. Completing a long-overdue repeat advanced it by exactly one occurrence, so a
   month-neglected weekly reminder needed four taps to stop being overdue — and
   the "moved to the next occurrence" message was a lie. Now it skips to the
   next *future* occurrence.
3. Deleting the date word from "Buy milk tomorrow" left the due-date chip
   behind, so the reminder still saved with a date the text no longer mentioned.
4. `RRuleExpander`'s infinite-loop guard counted only *yielded* dates, so the
   one case it existed for (a rule whose target date never exists, e.g.
   `BYMONTHDAY=31` pinned to February) still looped forever. Not reachable from
   today's UI, but Phase 5 will call this from a background worker.
5. The parser could create a repeating reminder already overdue at the moment of
   creation ("Gym every other week" typed in the afternoon → due today 09:00).
6. Opening Repeat from the typing-view chip stranded the user on the When
   screen afterwards; Detail's Repeat row crushed its label to one character per
   line once the value got long (both now fixed — the second is in CLAUDE.md).
Findings 4 and 5 were sent back to the subagents that owned those files, which
worked well: they still had full context and fixed them with tests.

**Judgment calls worth flagging (mine, not pre-approved in PLAN.md):**
- **Recognised date/repeat words are stripped from the saved title** — "Bins out
  every Tuesday at 7pm" saves as "Bins out". The design shows the raw text
  staying in the field with chips below it, and is silent on what gets stored.
  Stripping matches Samsung's behaviour and reads better in a list, but it *is*
  the app quietly editing what you typed. Easy to reverse if disliked.
- **Repeat is a sub-editor inside the Capture sheet**, not the standalone
  full screen the design draws. The sheet lives in its own window, so
  navigating away would destroy the in-progress capture. The editor itself is
  written to be movable, so this is reversible.
- `every weekday` / `every weekend` are summarised with those words rather than
  listing five day names, which wrapped the chip onto two lines.
- The Phase 2 gap (no full date/time picker in the When editor, quick-time
  presets only) is **still open** — a real date picker exists now, but only
  inside the Repeat editor's "On a date" option.

**Note on the parallel-subagent experiment:** it worked, and the two engine
pieces came back with genuinely good test suites. Two costs worth knowing:
concurrent Gradle runs against one project directory race on `build/` and
produce confusing phantom compile errors (now in CLAUDE.md), and I had to fix
the shared data contract up front so the workers couldn't disagree about it.
Worth repeating for phases with several independent, testable pieces; not worth
it for a phase that's one connected slab of UI.

## Phase 4 — Today view, swipe actions, checklists, list picker, Search — ✅ DONE (2026-08-17)

Split again across two parallel Sonnet subagents (Today screen, Search screen)
while I built the shared data layer and the Capture-side work. Notes on how the
split went at the bottom.

Built:
- **Today screen** (`today/`, design S04): Overdue / Later today / Completed
  today sections with the design's error-coloured overdue cards, a
  `Monday, 17 August · N reminders` subtitle, and the "Add to Today" pill.
  **Swipe one way to complete, the other to snooze 30 minutes**, each with an
  Undo snackbar.
- **Search screen** (`search/`, design S12): searches titles, notes *and*
  checklist item text in one query, with the matched substring highlighted,
  Open/Completed/Checklists filters, result counts, and recent-search chips
  persisted in DataStore.
- **Checklists**: add/edit/tick/remove rows in the Capture sheet, a checklist
  card on Detail with tickable rows, and "N of M" progress bars on Home cards
  and search results.
- **Inline list picker** in Capture, including creating a new list without
  leaving the sheet.
- **Undo infrastructure**: `ReminderUndoSnapshot` + `snapshotFor`/`restore`,
  because undoing a completed *repeating* reminder can't just flip a flag —
  completing one moves its due date instead.
- `seriesStartAt`-aware snooze, a `DISTINCT` search query, and DataStore added
  for the first time (Settings will reuse it in Phase 9).

Verified end-to-end on the `Pixel_9` emulator: searched "milk" and confirmed it
matched a reminder **via its checklist item** with the match highlighted;
swiped to complete and watched the item move to "Completed today" with an Undo
snackbar; tapped Undo and confirmed the reminder came back to Overdue intact;
swiped the other way and confirmed a 30-minute snooze moved it to "Later
today"; created a reminder with a ticked checklist item and confirmed the tick
survived the save. 75 unit tests still pass; no crashes in logcat.

Bug I found myself during verification: after a swipe, the row stayed visually
blank (just the swipe background) until the Undo snackbar timed out ~4 seconds
later, because the dismiss state was only reset *after* the snackbar resolved.
Now reset immediately after the action.

**Also worth recording:** I twice concluded Undo was broken when it wasn't —
the snackbar was simply expiring between my separate `adb` commands. Driving a
timed UI needs the action and the follow-up tap in a *single* device-side
command; otherwise the round-trip latency silently invalidates the test.

**`/code-review` found six issues, all real, all fixed before committing:**
1. Snooze clears the all-day flag but the undo snapshot didn't record it, so
   snooze-then-undo permanently converted an all-day reminder to a timed one.
2. The checklist's ticked state was thrown away on save — the tick box in the
   Capture sheet was effectively a dead control.
3. A crash: the search highlighter indexed the original string using offsets
   from a lowercased copy. Lowercasing can *change a string's length* (Turkish
   İ), so those offsets ran past the end and would crash the results list.
4. A user typing `%` or `_` into search hit SQL `LIKE` wildcards and got every
   reminder back. Now escaped, with `ESCAPE '\'` in the query.
5. The checklist save was a delete-then-insert with no transaction — a save
   cancelled mid-flight (the sheet's scope dies when it closes) could commit
   the delete and lose every item.
6. All-day reminders counted as overdue from 00:00, so they sat in the red
   Overdue section all day. They're now only late once the day is over.

**Deviations from the design worth flagging:**
- Both subagents discovered they **cannot access the `DesignSync` tool** — it's
  only available to the main session — so both built their screen from my
  written brief rather than the spec. I checked them against the real specs
  afterwards and corrected Search's filter chips and recent-search chips to the
  design's colours/shapes. Today matched closely. This is now in CLAUDE.md:
  fetch the spec and paste it into the prompt when delegating a screen.
- Search has a "Clear" button for recent searches that the design doesn't show
  (kept — otherwise the stored history can never be cleared).
- Checklist rows have no drag-to-reorder yet, though the design shows drag
  handles. Add/edit/tick/remove all work.
- The "Photos" search filter is present but stubbed, since photo attachments
  don't exist until Phase 8.

**Note on the parallel-subagent split:** cheaper than Phase 3 in wall-clock
terms, but the seams cost more — of the six review findings, four were in the
boundary between my data layer and the agents' screens. Fetching the design
specs for them up front would have removed most of the rework.

## Phases 5–12 — ⬜ NOT STARTED

See PLAN.md for full descriptions of each.

## Open product/technical TODOs carried from PLAN.md

- Recycle-bin retention length: defaulted to 30 days, not user-specified.
- Real app icon: still a placeholder.
- Voice recognition privacy disclosure wording: needed by Phase 8/9, not
  written yet.
