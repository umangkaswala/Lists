# Progress

> Read this file first in every new session — it says what's done and what
> to do next. Full phase descriptions are in [PLAN.md](PLAN.md); working
> conventions and environment setup are in [CLAUDE.md](CLAUDE.md).

**Current status: Phase 7 built, code-reviewed, and verified as far as an
emulator allows. It is NOT signed off — geofencing cannot be proven on this
emulator (Play Services refuses to activate registrations without a Google
account), so the walk-test at the end of the Phase 7 entry is a real
dependency. A spec re-check of Phases 1–4 has since fixed five more deviations
(see its own entry below). Start Phase 8 next (voice capture + photo
attachments). Umang has said he will run the Phase 5 and Phase 7 device tests
at the end rather than between phases.**

Phase 5's phone-only checks — real Doze, Samsung battery management, a reboot
with a secure lock screen, real sound and lock-screen presentation — were run by
Umang on 2026-08-18 and came back clean. The "Still needs the phone" list in the
Phase 5 entry below is kept as the script to re-run after any alarm change.

## 🔴 Outstanding bugs — fix these before starting a new phase

These are confirmed defects against the design, not ideas. Do them first.

*(None open right now. Bug 1 below is fixed — kept as a record of what was
wrong and why it wasn't caught, because the "why" still applies to the
Phase 1–4 screens that have not been re-checked yet.)*

### 1. The When editor had no way to set a date or a time (design S07) — ✅ FIXED 2026-08-18

**Found by Umang on 2026-08-18, testing the Phase 5 build on his own phone.**
He opened **When**, looked for a clock, and there wasn't one. His words: *"I
don't know how do i set the time for the reminder."*

At the time the only ways to give a reminder an arbitrary time were the four
quick-pick chips, or typing the time into the reminder text and letting the
parser catch it (`in 20 minutes`, `14:35`, `at 7pm`). Neither is discoverable,
and a reminder app where you can't pick a time is not finished.

**This was a straight spec deviation, not a design decision.** `S07When.dc.html`
specifies, directly under the All day toggle and above the quick chips:

- a **Date** field — label "Date", value e.g. "Tue, 19 Aug"
- a **Time** field — label "Time", value e.g. "7:00 pm"

Both 56dp tall, `secondaryContainer`, 16dp corners, side by side (Date takes the
remaining width, Time a fixed 140dp), each an 11sp label above a 16sp semibold
value. The chips got built and both fields were silently dropped.

**Second deviation on the same screen:** the design's quick chips are
**"In 1 hour" / "Tonight 7 pm" / "Tomorrow 9 am"** (three, outlined, naming the
actual resulting time). Mine were "Later today / Tonight / Tomorrow / Next week"
(four, no times shown).

**What was built to fix it** (`capture/CaptureSheet.kt`,
`capture/CaptureViewModel.kt`, `capture/QuickTimePresets.kt`):

- The two **Date / Time fields** at the design's sizes and colours, opening the
  M3 `DatePicker` and `TimePicker`. Both read "Not set" until they hold
  something. The time picker honours the phone's 12/24-hour setting.
- Date and time are **edited independently but stored as one instant**, so
  changing the day keeps 7:15 pm, and changing the time keeps Friday.
- The **three design chips**, whose labels now name the time they set.
- The three property rows below (Repeat / Early alert / Alert style) gained the
  design's leading icons and chevrons; Early alert and Alert style are still
  stubs, now reading "None" and "Sound + vibrate" as the design does.

**Judgment calls made while fixing it** (all flagged rather than silent, and all
in service of one rule — *never save a due time that has already passed, because
`AlarmPlanner` drops any trigger in the past and the reminder then never alerts
at all, with nothing on screen to say so*):

- Picking a **time with no date yet** means the next time it's that time —
  today if it's still ahead, otherwise tomorrow. Same as a clock alarm.
- Picking a **date with no time yet** gets 9:00 am, unless 9:00 has already gone
  on that date, in which case it gets the next whole hour.
- **All day** turned on with no date means all day *today*; turning it back off
  takes that invented date away again.
- A **quick chip or a picked time clears the All day flag** — an all-day
  reminder alerts at 09:00, so leaving the flag set would throw the chosen time
  away.
- Past 7 pm, the **"Tonight 7 pm" chip becomes "Tomorrow 7 pm"** rather than
  offering a time that has already gone.

**Verified on the emulator** by driving it as a user would: set 7:15 pm, changed
the date to Fri 21 Aug and confirmed the time survived, saved, and read the
scheduled alarm back out of `dumpsys alarm` — `RTC_WAKEUP ... origWhen=2026-08-21
19:15:00.000 exactAllowReason=permission`, i.e. the exact instant picked. Then
re-verified each of the four rules above on screen after the code-review fixes.

**`/code-review high` found 5 issues, all fixed before commit:** `setDate`
inventing a past 9:00 am; quick chips not clearing `isAllDay`; the All day
switch leaving its invented date behind; the presets frozen by `remember` so a
long-open sheet offered a stale "In 1 hour"; and the three longer chip labels
being clipped off-screen in a non-wrapping `Row` at large font scale.

**Why it went unnoticed for three phases:** every check we run is aimed
elsewhere. `/code-review` looks for correctness bugs, and the code was correct.
The Plan agent looks for platform traps. And my own emulator testing drove the
app *knowing how it works inside* — I typed `Standup 00:16 every weekday` because
I'd read the parser, so I never once went looking for a clock. I was testing the
implementation against itself.

**Also still to do:** re-check the Phase 1–4 screens against their `.dc.html`
specs, since the same blind spot applied to all of them and there may be more
deviations like this one.

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

> **Update (2026-08-18): this was not a defensible reading — it's a bug.**
> `S07When.dc.html` explicitly specifies Date and Time fields, so "chips only"
> was never what the design asked for, and Umang hit the gap immediately the
> first time he used the app on his own phone. See **"Outstanding bugs"** at the
> top of this file, which carries the full spec and the fix — built and
> verified on 2026-08-18.

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

## Phase 5 — Notifications, exact alarms, onboarding permissions — ✅ DONE (2026-08-18, confirmed on Umang's phone)

Written in the main session rather than split across subagents: this phase is
one tightly-coupled reliability surface, and Phase 4's lesson was that the
seams between parallel workers are where the bugs collect. The one piece that
*was* delegated — the Onboarding screen — is pure UI with a value-in/value-out
signature, and this time the design spec was fetched and pasted into the brief
up front (the fix for Phase 4's DesignSync problem). It came back clean.

**The `Plan` agent ran first**, as PLAN.md/CLAUDE.md require for this phase. It
was worth it: it caught the `USE_EXACT_ALARM` Play-policy trap, the
PendingIntent-identity-ignores-extras bug, the `exported="true"` requirement for
boot receivers, and the direct-boot limitation below — all before any code.

Built:
- **`notifications/AlarmPlan.kt`** — pure Kotlin, no Android imports: decides
  which reminders get an alarm and when. **10 unit tests** (all-day → 09:00,
  past-due skipped, completed/deleted excluded, cap + horizon, one alarm per
  repeating series, DST). Same approach that made the RRULE engine provable.
- **`notifications/AlarmScheduler.kt`** — the Android side. Every mutation
  triggers a **full idempotent re-sync** rather than per-reminder scheduling,
  which kills a whole family of bugs at once (stale alarm after an edit, orphan
  after a cascading list delete, double alarm after undo). Requests are
  conflated onto `applicationScope`, and the sync body is behind a `Mutex`.
- **`ReminderAlarms`** — a two-method, Android-free interface the repositories
  depend on, so `ReminderRepository`/`ListRepository` can trigger scheduling
  without importing the framework and stay unit-testable.
- **Notification** (design S17): title, `time · repeats … · list` subtitle, and
  **Done / 10 min / 1 hour** actions; tapping the body deep-links to Detail.
- **Receivers**: alarm → notify (`goAsync()`), Done/Snooze, and a boot receiver
  covering `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET` and
  `TIMEZONE_CHANGED`, enqueueing a WorkManager job.
- **Onboarding screen** (design S01) with all three permission rows live, shown
  once on first launch, gated by a DataStore flag.
- **Home permission banner** — appears when notifications are off or exact
  alarms are denied, because both failures are otherwise completely silent.
- Detail's previously-stubbed **Snooze** button now works (30 min, matching
  Today's swipe).

**Verified on the `Pixel_9` emulator (Android 16), not just compiled:**
- Set a reminder, **locked the screen**, and it fired at exactly the right
  minute with the design's layout and all three action buttons.
- **Tapped Done on a repeating reminder → it rolled forward to the next weekday
  and re-armed its alarm automatically** (Tue 00:16 → Wed 00:16). This is the
  single most important behaviour in the phase.
- Snooze from the notification moved the due time and re-scheduled.
- **Rebooted the emulator**: logcat shows the process started *for* the boot
  broadcast, the worker ran, and both alarms came back — with the app never
  opened. Alarms show `exactAllowReason=permission` and `flags=0x5`
  (`STANDALONE | ALLOW_WHILE_IDLE`), i.e. the Doze-permitted exact kind.
- Onboarding's three rows: the real POST_NOTIFICATIONS dialog, the real
  exact-alarm settings screen, and each row ticking green afterwards —
  including the exact-alarm one, which is granted *outside* the app and so
  relies on the on-resume re-check.
- Revoked notifications with `pm revoke` → banner appeared → tapped it →
  permission dialog → banner disappeared.
- Ticking a reminder complete **in the app** clears its notification.
- **Five reminders fired unattended across nine hours** while the session was
  idle — an accidental but genuinely useful soak test.
- 85 unit tests pass.

**`/code-review` (high) found eight issues, all real, all fixed:**
1. Marking onboarding complete ran on `rememberCoroutineScope()` and was
   cancelled by the navigation that immediately followed — onboarding could
   reappear forever. Exactly the scope-lifetime trap CLAUDE.md already warns
   about, and which `ReminderAlarms`' own doc comment describes.
2. Completing, snoozing or deleting a reminder **in the app** left its
   notification sitting in the shade with live Done/Snooze buttons. Now handled
   centrally in the re-sync, so no call site has to remember.
3. Rotating the phone after opening a reminder from a notification pushed
   Detail onto the stack a second time (the intent extra was never consumed).
4. A `TIME_SET` broadcast arriving just after boot — exactly when a phone
   corrects its clock off the network — cancelled the boot job via a shared
   unique-work name, silently dropping the missed-reminder catch-up.
5. `syncAll()` could run concurrently from the receiver and the boot worker;
   one pass's cancel-all could wipe alarms the other had just written.
6. Snoozing a *completed* reminder wrote a future due date that nothing would
   ever schedule — the snooze silently vanished.
7. POST_NOTIFICATIONS was only ever requested from the one-shot onboarding
   screen. Skip it, or deny twice, and the app would never alert again with no
   way back. Hence the Home banner.
8. The exact-alarm permission was re-read once per alarm (a binder call) inside
   the scheduling loop — ~800 round-trips per sync at the cap. Hoisted.

**Still needs Umang's phone — I can't honestly sign these off on an emulator:**
- **Real Doze.** The emulator is permanently "plugged in" and never truly
  sleeps. Test: set a reminder ~45 minutes out, screen off, phone left still
  and unplugged.
- **Samsung's battery management.** "Sleeping apps" / "Deep sleeping apps" /
  "Put unused apps to sleep" don't exist on an AOSP emulator and are the most
  common real-world cause of missed reminders. If a reminder is missed, check
  Settings → Battery → Background usage limits *before* assuming a code bug.
- **Reboot with a PIN/fingerprint lock** — see the direct-boot limitation below.
- Actual sound, vibration, lock-screen presentation and Do Not Disturb.

**Known limitation, deliberate and worth knowing:** after a reboot, reminders
are **not rescheduled until the phone is unlocked once**. The reminders database
is credential-encrypted, so a direct-boot-aware receiver would run before it can
be opened and would have nothing to read. Fixing it properly means keeping a
shadow copy of "id + due time" in device-protected storage — real, self-contained
work, better placed in Phase 12 hardening than bolted on here.

**Judgment calls worth flagging (mine, not pre-approved in PLAN.md):**
- **All-day reminders alert at 09:00 local.** They had to alert at *some* time;
  midnight would wake people up. Should become a Settings option in Phase 9.
- **Notification snooze offers 10 min and 1 hour.** The design shows four chips
  (10 min / 1 hour / Tomorrow / When I get home) but Android allows three
  actions, and "When I get home" needs Phase 7's places.
- **Reminders missed while the phone was off only alert if they came due within
  the last hour.** Waking to a burst of overnight notifications would be worse
  than the red Overdue section already on Home.
- **A repeating reminder that's never completed alerts once and then not again**
  until it's completed, because roll-forward happens on completion. Consistent
  with the existing model, but worth seeing before it's baked in.
- **No full-screen "rings until dismissed" alarm-style reminder.** Android 14
  restricts that to calling and alarm-clock apps and Play revokes it for others.
- **`SCHEDULE_EXACT_ALARM`, not `USE_EXACT_ALARM`.** The latter never has to be
  asked for but Play restricts it to alarm-clock and calendar apps.
- **`ACCESS_FINE_LOCATION` is declared now** (nothing reads it until Phase 7) so
  onboarding's Location row is a working control rather than a dead one.

**Pre-existing cosmetic issue noticed, not fixed:** Home's Overdue/Today
grouping is computed when the reminder data changes, not on a clock tick, so
leaving the app open past a due time doesn't re-group it until something else
changes. Phase 1 behaviour, unrelated to this phase's work.

## Phase 6 — Completed screen, Recycle bin, history — ✅ DONE (2026-08-18)

Design screens **S13 Completed** and **S14 Recycle bin + selection mode**, plus
the History card on **S11 Detail** that had been a placeholder since Phase 1.

### The one thing that shaped the whole phase: a completion log

Completing a *repeating* reminder never sets `isCompleted` — it rolls `dueAt`
forward to the next occurrence. So the reminder row keeps **no trace** of the
occurrence that was just finished. Built on the reminder row alone, the
Completed screen could never show a repeating reminder, "on time / 3 min late"
would have been measured against the *next* due date rather than the one that
was met, and Detail's streak card would have had nothing to count.

So Phase 6 added a **`completions` table** (`data/entity/CompletionEntity.kt`):
one row per tick-off, holding when it was done, the due instant it was measured
against, whether that occurrence was all-day, and what `dueAt` became
afterwards. Everything on the Completed screen, the chart, and Detail's history
is read from it.

**Schema version 2 → 3 with a real hand-written migration**, not the destructive
fallback. By this point the app is on Umang's actual phone with his actual
reminders on it, and wiping them to add a history table would have been a poor
trade. The migration also **backfills** already-completed reminders into the
log — their `dueAt` is still the date they were measured against, so the
punctuality those rows produce is real, not invented.

**The migration was tested by actually performing it**, not by reading it:
stashed the phase, built the v2-schema APK, wiped the emulator, seeded and
completed reminders under v2, then installed the v3 build over the top. Data
survived and the backfilled completions appeared on the Completed screen. Done
twice, because the DDL changed after the code review.

### What was built

**Completed (`completed/`, design S13)**
- Seven-day bar chart, card corner 24, bars corner 8 `primaryContainer` with
  today in `primary`. Purely descriptive — no goal line, no streak pressure, as
  the spec asks. Days with nothing on them keep a visible sliver, because an
  absent bar and a zero bar look identical and one of those reads as "broken".
- Rows grouped **Today / Last 7 days / by month**, paged 50 at a time behind a
  "Show older" button.
- Today's rows carry both clock times ("Due 7:00 am · done 7:12 am"); older ones
  carry the date and how close it was ("Sat, 15 Aug · on time", "1 min late").
- Trailing **undo** puts the reminder back; for a repeating one it restores that
  occurrence only, exactly as the spec words it.
- Overflow: **Delete all completed** behind a confirm dialog.

**Recycle bin (`bin/`, design S14)**
- 30-day retention notice, and every row states its own countdown ("Deleted 5
  days ago · 25 days left") rather than only the policy.
- Multi-select: selected rows switch to `secondaryContainer` with a filled
  checkbox, top bar becomes close / "N selected" / select all, and an 80dp
  bottom bar offers **Restore** (filled primary) and **Delete now** (outlined,
  error label) behind a confirm dialog.
- Overflow: **Empty recycle bin**.
- **30-day auto-purge** runs on every app start. That's enough: the bin is only
  ever *seen* from inside the app, so nothing can look overdue for deletion
  before the sweep has had a chance to run, and a WorkManager job would only
  have been a second thing to get wrong.

**Selection chrome (`ui/selection/SelectionBars.kt`)** is shared, not copied.
S14 says "the same selection mode is reachable from any list", and two lookalike
implementations would drift apart the first time either changed. Completed uses
it too, via the `checklist_rtl` icon in its top bar — see the judgment calls
below.

**Detail's History card** now shows the real thing: "12 completed · 4 on time in
a row" plus the last 8 completions with their dates and punctuality. Late
completions are tinted `onSurfaceVariant`, not error — they're still
completions, and tinting them red turns a history card into a scolding.

**Home's search bar** gained the **trailing overflow** the S02 spec always
called for (it had been a single "Your lists" button). Completed and the recycle
bin have no other way in, and hanging them off the menu the spec already puts
there beat inventing a navigation surface that isn't drawn anywhere.

### Judgment calls, flagged rather than silent

- **"Delete all completed" does two different things, because the screen shows
  two different things.** A finished reminder moves to the recycle bin. A
  repeating reminder is still running and is never `isCompleted`, so it keeps
  going and only loses its history rows. Binning a live series behind that menu
  item would have thrown away a reminder the user still expects to fire. The
  dialog says both halves out loud.
- **Delete goes to the bin, never straight to permanent.** Everywhere else in
  this app Delete means "recoverable for 30 days", and making one path permanent
  behind a single dialog would be a nasty surprise. Only the bin's own "Delete
  now" is permanent, and it says so.
- **The subtitle reads "311 completed", not the design's "311 reminders".** It
  counts completions, and one daily reminder ticked off all month is 30 of them,
  not 30 reminders. Accuracy won over matching the mockup's word.
- **Selection mode on Completed is an inference, not a spec.** S13 draws a
  `checklist_rtl` icon in its top bar but the spec text never says what it does;
  S14 says the same selection mode is reachable from any list. Making it select
  mode (with Undo / Delete) is the only reading that makes the icon real.
- **The bin's retention notice doesn't repeat the design's copy.** The mockup
  says deleted reminders are "removed from every signed-in device"; there are no
  signed-in devices in v1, so it says what actually happens instead of promising
  a sync that doesn't exist.
- **Undoing an older completion doesn't move the due date.** Only a reminder's
  most recent completion owns its current due date; moving it back from an older
  entry would step over occurrences still recorded as done and re-alert for all
  of them.

### Verified on the emulator

Driven as a user would, not just compiled: completed reminders and watched them
appear on Completed with the right due/done times; undid one and watched it
return to Home; selected one and binned it; restored it from the bin and
confirmed it came back **with its history intact**; completed the repeating
"Bins out", saw the occurrence logged while the reminder rolled to Aug 25, then
undid it and watched the due date go back to Aug 18; opened Detail and read the
history card ("1 completed · Tue, 18 Aug · 13 hours late" for a reminder due
1:07 am and ticked at 2:44 pm — arithmetic checked by hand); ran "Delete all
completed" and confirmed the screen emptied, two reminders went to the bin, and
the repeating series survived.

**The 30-day purge was tested for real.** The emulator is a production image, so
its clock can't be moved; instead the retention constant was temporarily set to
0, the app relaunched, and the bin confirmed empty — then the constant was put
back to 30 and rebuilt.

### `/code-review high` found 5 issues, all fixed before commit

1. **"Delete all completed" contradicted its own dialog** — it matched
   `isCompleted = 1`, but the screen reads the `completions` table, so a list
   full of repeating occurrences would report "Nothing to delete" and stay put.
2. **Undo could silently discard a due-date edit.** Complete a repeating
   reminder, edit its next date, then undo, and the edit was overwritten. Fixed
   by recording `nextDueAt` on the log row and only restoring the old date when
   the reminder's current one still matches it.
3. **The selection bar's Delete binned a whole repeating series** with no
   confirmation and no undo, unlike the bin's own delete. Now behind a dialog
   that names the count and says outright when a repeating reminder is included.
4. **The undo watermark was measured two different ways** — taken by
   `completedAt`, applied by `id >`. A device clock stepping backwards between
   two completions made them disagree, and undo would delete an entry it never
   created. Now taken by `MAX(id)`.
5. **"311 reminders" counted completions.** See the subtitle note above.

A copy bug found while re-verifying the fixes on screen — "1 reminder … where
*they* can be restored" — was fixed too.

### Known limitations

- **A repeating reminder still can't be finished for good** from the Completed
  screen; it only ever rolls forward. Ending a series is the Repeat editor's
  UNTIL/COUNT, which already exists.
- **Completions before the upgrade have no `nextDueAt`**, so undoing one of
  those backfilled entries won't restore its due date. Correct: the app can't
  know what the date was before it started recording.
- **The Completed list has no swipe actions.** The design doesn't draw any, and
  the trailing undo glyph covers the one thing a row needs to do.

## Phase 7 — Places & geofencing — ⚠️ BUILT, NEEDS UMANG'S PHONE (2026-08-18)

Design screens **S05 Places** and **S08 Capture — Where**, plus a Place row on
**S11 Detail**.

**This phase cannot be signed off on an emulator.** See "What the emulator
could and couldn't prove" below — the walk-test is a real dependency, not a
formality.

### The planning pass

PLAN.md and CLAUDE.md both call for the Plan agent before this phase. Umang had
asked for agents to be left alone, so the pass was done in the main session
instead. Five platform traps were identified up front and all five shaped the
code:

1. **Background location cannot be requested with a dialog from Android 11.**
   The system refuses to show one; a runtime request is denied on the spot with
   nothing on screen. The only route is a deep link to app settings — which is
   exactly what design S05's "Fix" button is for.
2. **Geofences are wiped by a reboot, by toggling location off and on, and by a
   force-stop.** Three separate re-registration paths, not one.
3. **The geofence PendingIntent must be `FLAG_MUTABLE`** on Android 12+. Play
   Services writes the event into it; an immutable one arrives empty.
4. **Android 12+ lets the user grant "approximate" location**, which reads as a
   granted permission but cannot drive a geofence at all.
5. **A geofence can't be given a schedule.** The design's "only between" window
   has to be applied when the crossing is reported, not when it's registered.

### What was built

**Data** — `places` (schema-only since Phase 1) became real, and reminders
gained `placeTrigger`, `placeWindowStartMinute`, `placeWindowEndMinute`,
`placeWindowDays` plus an index on `placeId`. **Schema 3 → 4 with a real
migration**, same reasoning as Phase 6: the app is on a real phone with real
reminders on it.

No foreign key from `reminders.placeId` to `places.id`: adding one to an
existing table means rebuilding it in SQLite, and the only rule it would
enforce — clearing the trigger when a place is deleted — is one query in
`PlaceRepository`.

**`GeofenceRegistrar`** — modelled on Phase 5's `AlarmScheduler`. Callers only
say "something changed"; one serialised worker re-reads the database and
reconciles. It implements the same `ReminderAlarms` interface the alarm
scheduler does, and `AppContainer` fans one signal out to both through
`ReminderSyncFanOut` — every reminder edit invalidates both, so wiring them
separately would have been a dozen call sites that must never disagree.

**`GeofenceReceiver`** — `goAsync()` and a `finish()` on every path, same as the
alarm receiver. Notification copy is the design's: "You just left Work".

**Re-registration** on app start, on `BOOT_COMPLETED` (via the existing
reschedule worker, which already waits for first unlock), on
`MODE_CHANGED`/`PROVIDERS_CHANGED` via a new exported receiver, and on a
`GEOFENCE_NOT_AVAILABLE` event.

**Places screen (S05)** — grouped by place with the address after "·", trigger
meta in tertiary with arrive/leave glyphs, per-place delete, the 90-of-100
warning, and a three-state permission banner.

**Capture — Where (S08)** — arrive/leave segmented button, saved-place chips in
`tertiaryContainer` (the design's rule that place triggers are tertiary
everywhere so they never read as times), Geocoder place search, the design's
100/200/500/1000 m radius slider, and the "only between" window with a
day-of-week row.

**Place search** uses the device's own `Geocoder` — PLAN.md's decision, no API
key and no billing. The trade-off is visible and the UI says so: no live
autocomplete, just type-and-search behind a Find button.

### Judgment calls, flagged rather than silent

- **No initial trigger.** Registering an "arrive at home" fence while already at
  home would alert instantly, and again on every resync. Only a real crossing
  counts. The cost: setting a reminder for where you already are does nothing
  until you leave and come back.
- **The radius belongs to the place, not the reminder**, and is saved the moment
  the slider moves. The editor says so ("Shared with every reminder on Lisbon").
- **Deleting a place keeps its reminders**, stripped of their trigger. Losing a
  saved location shouldn't silently delete the things you wanted to be reminded
  of there.
- **Every saved place is listed**, including ones with no reminders — otherwise
  there would be no way to delete them.
- **The bin's "removed from every signed-in device" copy was not reused.** There
  are no signed-in devices in v1.
- **No map.** Design S05's app-bar map action needs the Maps SDK and an API key,
  which PLAN.md explicitly avoided. Deferred with the reasoning recorded, not
  silently dropped.

### What the emulator could and couldn't prove

**Proved:** the 3 → 4 migration ran over the Phase 6 database with no data loss;
the Geocoder search returned real results ("Lisbon, Portugal") and saved a
place; the Where editor's trigger, chips, radius slider, window and day row all
work; the summary chip, the Places screen, and Detail's Place row all render as
designed; all three permission banner states appear and clear correctly; the
receiver is registered, reachable, and survives a malformed event without
crashing; and `addGeofences` is called with the right circle
(`38.7222,-9.1393 + 200 m, eventsFilter=[INSIDE], initialEventsFilter=[]` —
confirming the no-initial-trigger decision reached the platform).

**Could not prove:** an actual crossing firing a notification. This emulator
image is a production build with no Google account, so Play Services logs
`registration not active, registration not permitted` and never activates the
fence. Our client call succeeds; GMS declines. **No amount of emulator work will
close this gap** — it is why PLAN.md marks Phase 7 physical-device-critical.

**Compensating cover:** `PlaceWindowTest` — 11 cases over the "only between"
logic, the one piece that can be tested exhaustively without a phone and the one
most likely to be silently wrong (a broken window means the reminder alerts at
3 am, or never, with nothing in the logs).

### `/code-review high` found 7 issues, all fixed before commit

1. **The whole feature was dead.** `GeofenceRegistrar` refuses to register
   without `ACCESS_BACKGROUND_LOCATION` (correctly — Android 10+ requires it),
   but *nothing in the app ever requested it*, and the banner claimed place
   reminders merely "only work while Lists is open". They didn't work at all.
   Now requested properly, with the Android 10 dialog path and the Android 11+
   settings path, and the banner says "won't work" because that is the truth.
2. **Onboarding's Location button did nothing on Android 12+.** It requested
   `ACCESS_FINE_LOCATION` alone; without `ACCESS_COARSE_LOCATION` in the same
   call the system ignores the request outright — no dialog, straight to denied.
3. **The 100-geofence truncation was non-deterministic.** `activePlaceReminders()`
   had no `ORDER BY`, so `take(100)` kept an arbitrary subset that could change
   between syncs, while the warning promised "the oldest stop working". Now
   ordered newest-first, which is what the message describes.
4. **A `GEOFENCE_NOT_AVAILABLE` event was logged and dropped**, leaving every
   place reminder dead until the app was next opened — the exact failure the
   location-settings receiver exists to prevent, arriving by a route it doesn't
   cover. Now triggers a full rebuild.
5. **Every sync tore down all fences and re-added them.** Milliseconds, but a
   crossing during that gap is lost for good, and `setInitialTrigger(0)` means
   re-registering can't recover it — and `requestSync()` fires on every reminder
   edit. Now incremental: only fences that are actually gone are removed.
6. **A window with equal start and end was a one-minute window**, silencing the
   reminder 1439 minutes out of every 1440, while the editor said it ran
   overnight. Now the whole day, with the editor saying so, and two tests.
7. **The radius was saved on the Capture sheet's own coroutine scope** — the
   trap CLAUDE.md already records from Phase 4. Dismissing the sheet right after
   a slider drag could cancel the write, leaving the shown radius and the
   registered radius disagreeing. Now on the application scope.

Also fixed from the review's "worth knowing" list: `setPlaceWindowDays` had no
UI at all (the day-of-week row was added), several members were dead code and
were removed, and `updateReminderFields`'s `place` parameter lost its default so
a future caller can't silently strip a reminder's trigger.

### 🔴 Still needs Umang's phone — the walk test

1. Open **Places**. If a banner asks for location, tap **Fix / Allow** and
   choose **"Allow all the time"**. Place reminders do nothing at all until
   that is set — this is the single most likely reason for nothing happening.
2. Make a reminder, tap the **pin icon**, search for **home**, pick it, and
   choose **When I leave**. Set the radius to **200 m**.
3. Save it, then **physically leave** — a few hundred metres, on foot or by car.
4. Expect a notification saying **"You just left <place>"**.
5. Come back and repeat with **When I arrive**.
6. Then: **restart the phone**, unlock it, and repeat step 3 to confirm
   registrations survive a reboot.
7. Then: turn **location off and on again** in quick settings, and repeat
   step 3 — this is the case a receiver was added specifically to cover.

**Expect it to be slow.** Android batches geofence transitions to save battery;
a lag of a minute or two after crossing the boundary is normal, not a bug.
Below about 100 m the platform is unreliable, which is why the slider starts
there.

### Known limitations

- **No map view** (S05's app-bar map action) — needs a paid Maps API key.
- **Place names come from the Geocoder** and are sometimes a locality rather
  than the exact building. The place is saved under whatever name comes back;
  renaming it isn't possible yet.
- **The parser doesn't detect places in typed text** — "call mum when I get
  home" won't produce a place chip. Deliberate: the parser matches nothing that
  isn't already a saved place, and inventing places from free text is a bigger
  feature than this phase.
- **100 geofences per device**, warned at 90. Beyond that the oldest stop being
  registered.

## Spec re-check of Phases 1–4 — ✅ DONE (2026-08-18)

The item flagged after the missing-time-picker bug and never acted on: every
Phase 1–4 screen re-read against its `.dc.html` spec, looking for more silently
dropped requirements. **Five were found**, all real, none cosmetic.

### 1. Swipe-to-snooze was 30 minutes; design S04 says 1 hour

Three routes to snoozing (Today's swipe, Detail's button, the notification
action) and they disagreed: 30 / 30 / 60. Now all 60, with the number in one
constant per screen and a comment tying them together. The swipe *background*
still read "Snooze 30m" after the first fix — caught by the code review, in the
one place the user reads before committing to the gesture.

### 2. Search returned completed reminders by default

Design S12: *"Open is selected by default so completed items do not bury live
ones."* The filter started as null, so searching a word you use often came back
mostly things already ticked off. Now defaults to Open — and the empty state
had to change with it, because "No reminders match X" was then a lie for
anything completed. It now names the filter doing the excluding and points at
the Completed chip.

### 3. Recent searches couldn't be removed individually

Design S12: *"long-press to remove"*. Only "Clear" existed, so one mistyped
search meant losing the lot. Long-press now removes a single chip, with a
TalkBack label for the gesture.

### 4. Home showed tiles and filter chips on an empty database

Design S03: *"No tiles and no filter chips while the database is empty."* A
first-run screen showing "Today 0 · 0 overdue · 0 to go" above an invitation to
add something reads as broken rather than new. **Verified by reading, not by
observation** — the seed data means an empty database is unreachable in testing
without deleting every reminder by hand. It is a one-line guard on the same
flag the empty state two lines below already uses.

### 5. Detail had no overdue line

Design S11: *"overdue line 14/20 in error with an 18 dp error glyph."* Home and
Today both flag overdue in red; Detail — the screen you open to find out about
one — said nothing. Now shows "Overdue by 1 day" with the error glyph.

### `/code-review high` on the fix pass found 6 more, all fixed

The swipe label above, the misleading empty state above, plus:

- **Detail's overdue rule disagreed with Home's.** Detail carved out all-day
  reminders; Home and Today judge purely on `dueAt`. An all-day reminder could
  sit in Home's red Overdue section while its own page said nothing. Detail now
  matches the rest of the app.
- **The overdue line was frozen.** It was computed inside the view model's
  `combine`, which only re-runs when Room emits — so a reminder falling due
  while Detail was open never grew the line, and "Overdue by 1 min" stayed
  there indefinitely. Now a free function recomputed against a 30-second tick.
- **The recent-search chip painted a square ripple** over its 8 dp corner
  (`combinedClickable` added after Material3's own clip), and had no button
  role or long-press label for TalkBack.
- **The Open chip flashed unselected** for ~250 ms on entering Search, because
  the UI state's default and the flow's starting value disagreed and a debounce
  sat between them.

### Also ruled out, not a bug

Two reminders typed over adb came back with words missing ("Post the letter" →
"Post"), which looked like the parser eating input. It isn't: a unit test
(`PlainTitleTest`) confirms ordinary words survive parsing untouched. It was
`adb shell input text` dropping text after spaces on this emulator image. The
test is kept as a regression guard, because "the parser ate my title" is a
failure that would be easy to miss and hard to explain.

## Phases 8–12 — ⬜ NOT STARTED

See PLAN.md for full descriptions of each.

## Open product/technical TODOs carried from PLAN.md

- Recycle-bin retention length: defaulted to 30 days, not user-specified. Now
  a real constant (`BIN_RETENTION_DAYS`) that the bin screen's own copy reads
  from, so changing it changes what the app says.
- Real app icon: still a placeholder.
- Design S05's full-bleed map of all geofences: deferred in Phase 7, needs the
  Maps SDK and a billable API key, which PLAN.md deliberately avoided. Worth an
  explicit decision before Phase 12.
- Voice recognition privacy disclosure wording: needed by Phase 8/9, not
  written yet.
