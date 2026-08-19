# Progress

> Read this file first in every new session — it says what's done and what
> to do next. Full phase descriptions are in [PLAN.md](PLAN.md); working
> conventions and environment setup are in [CLAUDE.md](CLAUDE.md).

**Current status: Phases 0-9 built. Phase 9 added the Settings screen (design
S16) — every row on it does what it says — plus the privacy page, the real
Roboto Flex font, and the "nudge me again" alert behaviour. Phase 10 (widgets,
set A) is next.

Two things are outstanding and neither is a regression:

- **Phase 7 (places/geofencing) is not signed off** — geofencing cannot be
  proven on an emulator at all. See the device-test list immediately below.
- **The When editor's "Early alert" row is still a dead control**, found while
  verifying Phase 9 and deliberately left alone. See the end of the Phase 9
  entry for why, and for the recommendation.**

## 🔵 Device tests Umang still owes — keep this list

Umang has said, twice, that he will do all device testing **at the end** rather
than between phases. This is the running list; nothing here is a known bug, it is
all "cannot be proven on an emulator".

1. **Phase 5 — alarms.** Real Doze, Samsung battery management, a reboot with a
   secure lock screen, real sound/vibration/lock-screen presentation. *(Umang ran
   this once on 2026-08-18 and it passed. The script in the Phase 5 entry is kept
   to re-run after any alarm change.)*
2. **Phase 7 — the geofence walk test.** The full script is at the end of the
   Phase 7 entry. **This is a real dependency, not a formality** — Play Services
   never activates a fence on this emulator, so no amount of emulator work can
   close the gap.
3. **Phase 8 — actually speaking a reminder.** The emulator has no microphone.
   The recogniser launches correctly with our own prompt; whether dictated words
   arrive and parse can only be checked by talking to it. Now also from the
   **Today** screen's pill, which was fixed in the audit below.
4. **Phase 8 — taking a photo with the real camera.** The gallery path is
   verified; the camera path is verified only as far as launching the camera.
5. **Cold-start time.** 9.2 s on a loaded emulator. Worth re-measuring on real
   hardware — it should be far quicker, and if it isn't, that's worth knowing.

## 🔴 Outstanding bugs — fix these before starting a new phase

These are confirmed defects against the design, not ideas. Do them first.

**1. The When editor's "Early alert" row does nothing.** Found on 2026-08-19
while verifying Phase 9. It shows "None" and a chevron and taps to a "coming
later" message; it has been inert since Phase 2 and the dead-control audit
missed it. Not fixed in Phase 9 on purpose — it needs a schema migration and a
second alarm per reminder, which is real work on the alarm engine rather than a
settings fix. Full reasoning and the recommendation are at the end of the
Phase 9 entry.

*(Bug 2 below is fixed — kept as a record of what was wrong and why it wasn't
caught, because the "why" still applies to the Phase 1–4 screens that have not
been re-checked yet.)*

### 2. The When editor had no way to set a date or a time (design S07) — ✅ FIXED 2026-08-18

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
deviations like this one. — **Done 2026-08-18**, five deviations found (see
"Spec re-check" below), and a follow-up **dead-control audit** found nine
controls that did nothing because the phase they were waiting for had already
shipped.

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
  handles. Add/edit/tick/remove all work. **Fixed in the dead-control audit
  below (2026-08-18).**
- The "Photos" search filter is present but stubbed, since photo attachments
  don't exist until Phase 8. **Made real in the dead-control audit below
  (2026-08-18).**

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

## Phase 8 — Voice capture & photo attachments — ✅ DONE (2026-08-18)

The last two stubs in the Capture sheet's action row, plus the mic on the
capture pill and in Search.

### Voice capture

Uses `RecognizerIntent`, handing the job to whichever speech app the phone
already has, rather than the `SpeechRecognizer` API. Two reasons, both worth
recording:

- **No `RECORD_AUDIO` permission.** The recogniser app holds it and shows its
  own listening UI. Lists never touches the microphone itself.
- **That is a much easier thing to say honestly** in the privacy copy Phase 9
  has to write.

Dictated text goes into the **same Capture sheet, through the same parser**, so
"call mum tomorrow at six" arrives with its chips already filled in — spoken
input gets no special path and no second implementation. Search's mic fills the
query box rather than searching immediately, so a misheard word can be fixed
before it returns nothing.

`EXTRA_PREFER_OFFLINE` asks the recogniser to stay on the device. It is a
preference, not a guarantee — the text may be produced in Google's cloud
depending on what the phone supports. **That needs saying in Settings copy in
Phase 9**; it is already flagged in PLAN.md's open TODOs.

A phone with no dictation app at all gets a snackbar, not a dead button.

### The parser gained spoken hours

Writing a test for dictation-shaped input immediately failed: *"call mum
tomorrow at six"* parsed the date but left **"at six" stranded in the title**,
because the parser only understood digits. People do not dictate "at 6 pm".

Now "at six", "at six pm" and "at six o'clock" all parse. A **bare** spoken hour
is read literally — "at six" is 06:00, exactly as "at 6" already was. The first
attempt guessed PM for one-to-seven because that is usually what people mean,
and the code review rightly killed it: it made "at 6" and "at six" land twelve
hours apart, and made "at seven" evening while "at eight" was morning. One
consistent rule beats a clever inconsistent one.

### Photo attachments

**Storage.** Images are *copied* into the app's own files directory, not
referenced where they sit. A `content://` URI from the picker is a temporary
grant that stops resolving once the receiving process dies, and a gallery image
can be moved or deleted by its owning app at any time — either way the
attachment silently becomes a broken thumbnail. Only the file *name* is stored
in the database.

**Permissions: none added.** The gallery uses the system photo picker, which
needs no storage permission and shows only what the user picks. The camera uses
`TakePicture` with a FileProvider URI, and Lists deliberately does **not**
declare `CAMERA` — declaring it would *require* granting it before the camera
app could be used at all.

**Schema 4 → 5** with a hand-written migration, same reasoning as Phases 6 and
7. Nothing to backfill.

**File cleanup.** The files are not in the database, so nothing removes them
when a reminder is deleted for good — the row cascades away and the image would
stay on the phone forever. An orphan sweep runs at app start and deletes any
file no row points at. Files written in the last hour are skipped: a photo
attached to a *new* reminder exists on disk before the reminder is saved, and
the sweep racing that window would delete the picture out from under the sheet.

**Thumbnails** are decoded off the main thread with `inSampleSize` sized to
what is actually drawn. A modern phone camera image decoded at full size to
draw at 72dp is how a row of photos becomes an OutOfMemoryError.

### `/code-review high` found 5 issues, all fixed

1. **Removing a photo while editing deleted it immediately** — so backing out of
   an edit without saving still destroyed the picture, unlike every other field
   on the sheet. Now marked for deletion and committed in `save()`.
2. **A successful photo could be thrown away.** The pending capture path was in
   a plain `remember`, but the camera app can push this activity out of memory;
   the result then arrived with nothing to attach it to and reported "couldn't
   add that photo". Now `rememberSaveable`.
3. **The spoken-hour rule contradicted the digit rule** — see above.
4. **A filesystem syscall per photo per recomposition.** `fileFor()` went
   through a getter that called `mkdirs()` every time, and Detail's new 30-second
   overdue tick recomposed the whole screen forever, including for reminders
   with no due date. The directory is created once now, and the tick only runs
   while there is a due date to count against.
5. **A non-atomic state write** from the IO dispatcher could drop a keystroke or
   the photo if the two landed together. Now `MutableStateFlow.update {}`.

### Verified on the emulator

Migration 4 → 5 ran over the Phase 7 database with no data loss; a photo was
picked from the gallery, copied into app storage, shown as a thumbnail with its
remove button, saved, and found again on Detail under a "PHOTO" card after a
full reinstall; the Photo action icon lights up when a photo is attached; the
photo-source menu offers camera and gallery; and the capture pill's mic launches
the recogniser with our own prompt ("What do you need to remember?").

**Not verifiable here:** actually speaking. The emulator has no microphone, so
the recogniser launches but nothing can be dictated. The half that *can* be
covered — what the parser does with dictation-shaped text — is now two unit
tests. Speaking a reminder is on Umang's device list.

### One thing seen and deliberately not "fixed"

The emulator threw an **ANR** during this phase. It was investigated rather than
ignored: the reason was `No response to onStartJob`, the main thread was
`Runnable` inside Compose's *first composition* with no frame of ours on the
stack and nothing blocking, and the recorded system load was **11.29** because a
Gradle build was running on the same machine. There were also ANR files from
earlier in the day, before any Phase 8 code existed. This is a slow x86 emulator
under load, not a defect — but it is written down here so a future session
doesn't rediscover it and go looking for a bug. Cold start on the loaded
emulator measured 9.2 s; worth re-measuring on Umang's phone during the device
pass.

### Known limitations

- **Photos can't be opened full-screen** from Detail yet — they show as
  thumbnails. A viewer is Phase 12 polish, not a stub with a dead tap.
- ~~**No photo search.**~~ Fixed in the dead-control audit below.
- **Dictation language** follows the phone; there is no in-app language picker.

## Dead-control audit of every built screen — ✅ DONE (2026-08-18)

Not a phase. After the Phase 1–4 spec re-check found five silently dropped
requirements, the obvious next question was whether the same thing had happened
to **controls that were stubbed in an early phase and never revisited once the
thing they needed existed**. It had, nine times.

The audit read every screen's `.dc.html` spec against the code, then grepped the
whole codebase for every remaining `notYetAvailable(...)` call and every "coming
soon" string, and asked of each one: *does the feature it is waiting for exist
yet?*

**This is a different failure from a spec deviation.** Nothing here was built
wrong. Each one was correctly deferred at the time and then never picked back up,
because no phase owns "go back and finish what an earlier phase parked". Nine
buttons and rows in a shipped-feeling app did nothing but apologise.

### The nine

1. **Detail's Delete did nothing** — it showed "Deleting is coming in a later
   phase". The recycle bin has existed since **Phase 6**. The single most
   destructive-looking control on the screen was inert for two phases.
2. **Detail's Share did nothing.** Now shares the reminder as plain text through
   the system share sheet — title, due date, repeat rule, place, note and
   checklist. Nothing to do with S15's "Shared with 2 people", which still needs
   the account system v1 doesn't have.
3. **Detail's Due row did nothing** ("Changing the date is coming in a later
   phase") — the When editor has existed since **Phase 2**.
4. **Detail's List row did nothing** — the list picker has existed since
   **Phase 4**.
   S11 is explicit: *"Tapping a row opens that editor directly."* All four rows
   now open their own editor — Due → When, List → List picker, Repeat → Repeat,
   Place → Where — rather than dumping the user in the typing view to find the
   right icon.
5. **Today's pill mic did nothing.** Phase 8 wired dictation into Home and
   Search and missed the third pill — so the one screen whose entire job is
   "what's due now" was the only place the mic apologised.
6. **"Add to Today" didn't add anything to today.** S04: *"Capture pill label
   becomes 'Add to Today' and pre-fills today's date chip."* It opened a blank
   sheet, so a reminder added from the Today screen got **no due date at all**
   and then didn't appear on Today. Pressing the button on a screen called Today
   produced something that wasn't on it.
7. **Checklist: Enter did nothing.** S09: *"'Add an item' is a permanent last
   row — Enter on the keyboard creates the next one."* Every item needed a tap
   on "Add an item" first. Enter now creates the next row **and moves the caret
   into it**, so a five-item list is typed without touching the screen.
8. **Checklist rows had no drag-to-reorder** despite S09's drag handle and
   "long-press to reorder" — flagged as a known deviation back in Phase 4 and
   never picked up. Order is the whole point of a checklist; the steps of a
   recipe are not a set.
9. **Search's Photos filter did nothing.** Attachments have existed since
   **Phase 8**; the chip still said "Photo search is coming in a later phase".

**And one that wasn't dead, just empty:** the Lists screen's support line read a
flat **"List"** under every list name. S15 says *"Support line carries the
meaningful state"*, and "List" carries none. It now reads "6 reminders" — or
"Default list · 6 reminders", or "Nothing to do".

### How the fixes were built, where it mattered

- **`CaptureTarget` gained an `initialMode`**, so a caller can open the sheet
  straight onto a sub-editor. That is what makes S11's "opens that editor
  directly" possible at all.
- **Detail's delete runs on the application scope, not `viewModelScope`.**
  Deleting pops Detail off the back stack, which clears the view model — a bin
  write launched there would be cancelled by the very navigation that follows.
  This is the scope-lifetime trap CLAUDE.md already records twice, and it would
  have looked exactly like "sometimes delete doesn't work".
- **Delete goes to the bin behind a confirm dialog** that says so, and says
  outright when a repeating reminder will stop repeating. Consistent with the
  same decision made in Phase 6: Delete means "recoverable for 30 days"
  everywhere except the bin's own "Delete now".
- **`modeBeforeRepeat` had to move above `init`.** A property initialiser runs
  *after* the init block, so a `var` declared below it silently overwrites
  whatever init assigned — and init now opens sub-editors. Left alone this would
  have been a genuinely baffling bug: opening Repeat from Detail would land the
  user on the When panel on the way out.
- **The Today prefill is a _default_, not a choice.** Typing "call the bank
  tomorrow" still moves the chip to tomorrow; only the absence of a date in the
  text leaves today's default in place. A one-off write at startup would have
  been wiped by the first keystroke, because the sheet adopts the parse wholesale
  for any field the user hasn't taken over.
- **The reorder gesture asks the view model whether a move happened** instead of
  bounds-checking against a list it captured when the drag began. A
  `pointerInput` block does not necessarily see later state, and a stale list
  would have made dragging silently stop working after a row was added.

### Verified on the emulator, control by control

Every one was driven as a user would, not just compiled:

- Detail's **Due** row opened the When panel pre-filled with the reminder's own
  date and time; the **List** row opened the picker with the current list
  already selected.
- **Delete** showed its dialog, binned the reminder, popped back to Home, and
  the counts dropped (Today 3 → 2, Work 2 → 1). The reminder was then found in
  the recycle bin reading "Deleted today · 30 days left", and restored intact —
  proving the write survived the navigation that clears the view model.
- **Share** opened the system chooser with the preview "Send the quarterly
  report / Due: Aug 18, 2026, 5:11 PM".
- **"Add to Today"** pre-filled a "Today, 7:00 pm" chip at 6:20 pm — the next
  whole hour, the same rule the When editor's date field uses. Typing "Call the
  bank tomorrow" then moved the chip to **Tomorrow**, confirming the default
  yields to the text.
- **Today's mic** launched the recogniser with our own prompt.
- **Checklist Enter**: "One", Enter, "Two", Enter, "Three" typed three rows
  without a single screen tap.
- **Checklist reorder**: long-pressed the handle and dragged "One" two rows down
  → "Two, Three, One". Then dragged it *past* the end, held, and dragged back up
  two rows → back to "One, Two, Three", confirming the end-of-list case doesn't
  strand the row or bank up movement it has to pay off later.
- **Search's Photos filter**: "6 results" for a query became "1 result" — the one
  reminder with a photo on it.
- **Lists** rows read "Default list · 6 reminders" and "2 reminders", matching
  Home's tiles exactly.

98 unit tests pass; no crash or ANR in logcat throughout.

### Three problems found by my own review, before the code review ran

1. **The reorder gesture bounds-checked against a stale list** — see above. Fixed
   by making the view model authoritative.
2. **A fast drag lost movement.** The handler applied at most one row per
   callback and then discarded the remainder, so a quick two-row drag moved one
   row. Caught by the emulator disagreeing with itself between two runs of the
   same gesture, which is exactly the kind of thing that gets written off as
   "adb being flaky" rather than investigated. It now consumes the accumulated
   distance in a loop.

3. **Home presented a screenful of zeros as fact on every cold start.**
   `HomeUiState.isLoading` existed and was set correctly — and no screen ever
   read it. So for as long as the first Room query took (about a second on the
   emulator, caught by accident in a mid-load screenshot) Home painted the
   default state as though it were real: "Today 0 · 0 overdue · 0 to go" above
   list tiles reading 0. **Home was the only one of the four list screens whose
   `isEmpty` ignored `isLoading`** — Today, Completed and the recycle bin all
   define it as `!isLoading && …`. Now it matches them.

   The first attempt at that fix was itself wrong, and worth recording: deriving
   `isEmpty` from `sections.isEmpty()` looked equivalent and wasn't. Selecting a
   list filter with nothing open in it empties the sections while the database is
   full — which would have shown the first-run "Nothing to remember yet" screen
   to someone with plenty of reminders, **and hidden the very filter chips needed
   to get back out of the filter.** It now keys off the reminders table itself.
   Caught by asking "what else empties `sections`?" before believing the green
   build.

### `/code-review high` — ran late, found 8, seven fixed

CLAUDE.md makes `/code-review` step 4 of every phase, before committing. It was
attempted **nine times** while the audit was being built and every attempt came
back `API Error: 529 Overloaded` — a service outage, not a repo problem. The
audit was therefore committed (`b551ed9`) with the step recorded as owed rather
than quietly skipped. The service recovered the next session and it ran on
`b551ed9`'s diff. It found **eight issues; all eight were real**, which is the
argument for not treating a careful self-review as a substitute.

1. **The Lists screen had the same "zeros as fact" bug this very commit fixed on
   Home.** `openCounts` started as an empty map, and an empty map does not mean
   "no data yet", it means *every list has nothing in it* — so a full app read
   "Nothing to do" under every list name on the way in. Fixed the same way Home
   was, and the fact that I fixed one and shipped the other in a single commit is
   the useful lesson: a bug found in one place is worth grepping for in the rest.
2. **Enter on a middle checklist row appended a blank item at the bottom** and
   jumped there, instead of moving to the next existing row. Keyboard navigation
   through an existing list was impossible, and tabbing through a five-item list
   to fix a typo would have left five blank rows behind it. Enter now only
   *creates* on the last row; everywhere else it moves.
3. **The drag handle was a bare 20.dp icon** — well under the 48.dp minimum
   touch target, and sitting directly beside a full-size Remove button. The cost
   of missing was deleting the item you meant to move. Now a 20.dp glyph inside a
   48.dp target.
4. **The drag step distance was hard-coded to 44.dp** while the real row is
   ~46.dp, because `IconButton` enforces its own minimum height. Every step
   under-counted by ~4%, so the further a row travelled the further it drifted
   from the finger. It now measures a real row with `onGloballyPositioned`
   instead of assuming.
5. **Detail's share error was unreachable and misleading.** `createChooser`
   always resolves, so "Nothing on this phone can share text" could never fire
   for the reason it claimed — and any *other* failure would have been reported
   as that. Message and comment now say what the guard actually does.
6. **`SearchFilter`'s doc comment claimed `PHOTOS` "deliberately isn't a member
   of this enum"** — directly above the line adding `PHOTOS` to the enum.
7. **A new function was pasted between `fileFor`'s KDoc and `fileFor`**, so the
   doc described the wrong declaration.

**The eighth was not fixed, because it is a product decision, not a defect** —
see "needs Umang's call" below.

**Re-verified on the emulator after the fixes** (not just rebuilt): Enter on the
first of three rows moved to row two and added nothing; dragging still reorders
and now works from the edge of the target that used to be dead space; a 2.5-row
drag moves exactly two rows in *both* directions and round-trips. That last check
matters — an earlier test at exactly 2.0 rows moved 2 down but 1 up and looked
like a directional bug, when it was the test sitting precisely on the rounding
boundary. The reordered list survived a save, reopened from Detail in the right
order, and shared in that order too. 98 unit tests pass; no crash in logcat.

### The eighth code-review finding — property rows now commit (Umang's call, 2026-08-18)

The review's remaining finding was that tapping Detail's "List" row, picking a
list, and swiping the sheet away discarded the change silently. That was not a
regression — the sheet has always been a draft for every field — but the mental
model differs: "Edit" invites a draft, while tapping one property row reads as
"set this to that". Put to Umang as a product question; he said to go with the
recommendation, which was to make it commit.

**Now: opening the sheet straight onto one sub-editor commits and closes when
that editor is left.** Tapping Due, List, Repeat or Place on a reminder's page
changes exactly that one thing and returns to the page. Everything else is
untouched — the Edit button, the capture pill and "Add to Today" all still hold
a draft until the send button is pressed.

Two details worth recording:

- **Repeat's Cancel closes without writing.** Every other exit commits on the
  way past, but a button labelled Cancel that saves anyway would be a lie. It
  needed a separate "close without saving" signal on the state, since the sheet
  previously only ever closed on a successful save.
- **The commit is guarded by `canSave`.** `save()` refuses a blank title, so
  without the guard the back arrow would have done nothing at all and trapped
  the user in a panel with no way out.

**Verified on the emulator, all four paths plus the non-regression:** the List
row moved the reminder from Work to Personal and the Home tiles followed it
(Personal 7→8, Work 2→1) — proving it reached the database, not just the
screen; the Due row's back arrow committed "Tomorrow 9 am" and the "Overdue by 4
hours" line disappeared; Repeat's Save committed "Every Wednesday"; Repeat's
Cancel left it reading "None". And the path that must *not* change: Edit → type
into the title → swipe the sheet away still discards, exactly as before.

### Deliberately NOT fixed — these need Umang's call

- **Today's `filter_list` and overflow buttons are still dead.** S04 *draws*
  both but the spec text never says what either does. Guessing is how you end up
  with a filter nobody wants; the Completed screen's select-mode icon was already
  one inference too many. **Question for Umang: what should these do?** A
  plausible answer is filter-by-list and a "select several" mode.
- **S14 says selection mode is reachable "from any list via long-press".** It
  exists on Completed and the recycle bin only; Home, Today and Search have no
  long-press. That is a feature, not a stub — worth its own slot rather than
  being smuggled into an audit.
- **S14's selection top bar has no overflow**, which the design draws. Nothing
  obvious to put in it yet.
- **Detail has no Alert style row and no top-bar overflow**, both of which the
  S11 mockup shows. Alert style belongs with Phase 9's Settings work, since
  that's where the notification channel decisions live.
- **S11's "Edited 14 Aug 2026 · synced" footer** is not built, and the "synced"
  half would be a lie in a local-only app.
- **S06's "keyboard suggestion strip mirrors the parse"** is not built. The
  parsed chips already show the same information directly above the keyboard.

## Phase 9 — Settings, appearance, privacy & Roboto Flex — ✅ DONE (2026-08-19)

Design S16 built for real, plus the two things PLAN.md parked here: the privacy
copy Phase 8 owed, and the actual Roboto Flex font deferred from Phase 0.

Before this phase the app had **no Settings screen at all** — no route, no
package. It is now reachable from Home's overflow, and every row on it does
what it says.

### What each row actually does

**Alerts**

- **Alert style** — reads the live channel ("Sound + vibrate" / "Sound only" /
  "Vibrate only" / "Silent" / "Off") and opens **Android's own** channel
  settings. See the decision below; this is not an in-app editor and can't be.
- **All-day reminders arrive at** — a real time picker, default 9:00 am.
  `AlarmPlanner.ALL_DAY_ALERT_TIME` was a constant with a `// Phase 9 should
  make it a setting` note on it; it is now a parameter on every function that
  needs it, with the old value as the default.
- **Nudge me again if ignored** — new behaviour, see below.
- **Dismiss on every device** — drawn *on* in the design, shipped **disabled**
  with the copy "Needs an account. Lists keeps everything on this phone."

**Capture**

- **Read dates and places from my text** — turns the parser off. With it off,
  the title is stored exactly as typed. "Add to Today"'s prefilled date still
  applies, because that came from a button the user pressed rather than from
  their words.
- **Quick times** — its own sub-screen; the three When-editor chips.
- **Saved places** — opens the existing Places screen, and its support line
  lists the saved place names.

**Appearance** — the three-segment Light/Dark/System control and
**Colour from wallpaper** (Material You dynamic colour, Android 12+; the row
says "Needs Android 12 or later" and is disabled below that). Both repaint the
whole app live.

**Data** — **Keep deleted items** (7/14/30/60 days) and **Export my reminders**.

**About** — **Privacy** and **Version**. Not in the S16 mock-up: PLAN.md
requires a privacy page and the design gives it nowhere to live. Wedging it
into "Data" would have been wrong — that group is about the user's reminders,
this is about the app.

### The decision that mattered most: Alert style is not an in-app editor

CLAUDE.md already records that **a notification channel is immutable once
created** — importance, sound and vibration can never be changed by the app
afterwards, only by the user. S16 draws Alert style as a row with a chevron,
which reads like an in-app picker. Building one would have left exactly two
options, both bad: a screen whose controls silently do nothing, or deleting and
recreating the channel under a new id every time, throwing away every tweak the
user had made in system Settings.

So the row **shows the real state and opens the real switches**. The value line
is read from the OS on every `ON_RESUME`, because the user changes it outside
our process and we are never told. That reading is shared between Settings and
the Capture sheet (`rememberAlertStyleSummary`) rather than written twice — the
second copy is where the resume observer gets forgotten.

This also settles the "notification alert style" question that has been sitting
in the decisions-owed list since Phase 5: **there is nothing to decide**. The
channel keeps IMPORTANCE_HIGH + default notification sound + vibrate, which is
what the design specifies, and it is the user's to change from there.

### "Nudge me again if ignored" — a deliberately separate mechanism

This is new behaviour, not a toggle over something that existed.
`ReminderNudge` has its own receiver and its own PendingIntent namespace
(`lists://nudge/$id/$attempt`), and **`AlarmScheduler` was not touched**.

That separation is the point. The scheduler's whole design is "cancel every
alarm and re-derive them from the reminders table", and a nudge isn't derivable
from the table at all — it depends on transient facts (an alert was posted; it
is still sitting unanswered) that no column records. Folding it in would have
meant either persisting notification state or making the planner impure, on the
single highest-risk surface in the app.

**Nothing cancels a pending nudge when the user deals with the reminder**, and
that is on purpose. The receiver re-checks the world when it fires, which
catches every way a reminder can be answered — Done, Snooze, tapping it,
swiping it away, completing it in the app, deleting it — with one rule instead
of six call sites that each have to remember. "Ignored" is defined as *the
notification is still in the shade*, read from
`NotificationManager.activeNotifications`. Cost: at most one wasted wake-up per
reminder, the same trade `AlarmScheduler` already makes for stale alarms.

**The copy says "about every 10 minutes", not "every 10 minutes"** as the
design does. Measured on the emulator, Android gave the nudge alarm a
**7½-minute window** — it is an inexact `setAndAllowWhileIdle`, on purpose:
spending the exact-alarm budget to be punctual about a reminder the user is
already ignoring is the wrong trade. But then the design's flat "every 10
minutes" would be a small lie, so the word "about" is doing real work.

Place-triggered alerts deliberately don't nudge. "You just left Work" repeated
ten minutes and half a mile later is telling the user something that is no
longer true.

### Two settings that would have looked like they worked and quietly wouldn't

Both found by driving the app, not by reading it:

1. **Changing the all-day time left every existing alarm on the old time.**
   The alarms had already been registered for 9:00; nothing re-derives them
   until the app next resumes. Verified against `dumpsys alarm`: the reminder
   was still pending at 09:00 after the setting said 10:00. `setAllDayAlert…`
   now requests an alarm re-sync.
2. **Shortening the retention window didn't delete anything** until the next
   app start, so "keep for 7 days" with 30-day-old items in the bin was a
   setting that wasn't true yet. It now purges immediately — and the dialog
   says so outright, because this destroys data.

The purge runs on `applicationScope`, not `viewModelScope`: choosing "7 days"
and immediately pressing back would otherwise cancel the delete halfway. That
is the scope-lifetime trap CLAUDE.md records from Phase 4, and it applies to
one write on this screen.

### Quick times are stored as rules, not as times

"Tonight 7 pm" has to mean 7 pm whenever the sheet is opened, so what's saved
is the *shape* of each chip — a relative duration, an evening time, a
next-morning time — and the label is regenerated every time. A saved label
would go stale the moment the time behind it changed.

The rollover rules are now eight unit tests
(`QuickTimePresetsTest`), including the one that actually matters: **every chip
must be in the future at every hour of the day.** A chip that sets an instant
already gone is never scheduled at all, so it silently does nothing — and the
boundary case (exactly 19:00) is the easy one to get wrong.

### Roboto Flex, bundled

One 1.8 MB **variable** font file rather than five static cuts, with each
weight (300/400/500/600/700) registered explicitly through `FontVariation`.
Without the explicit registration Compose has only one outline and fakes the
rest by smearing the glyphs — which is exactly what a "600" heading is meant to
avoid. Bundled rather than fetched via downloadable Google Fonts, so the app
never renders a frame in the wrong face and works with no Play Services at all.
APK went 24.2 MB → 25.2 MB.

`Typography` has no iterable form, so the family is applied to all fifteen
styles by hand. A style missed there renders in the system font while
everything around it doesn't — far harder to spot than a wholesale failure.

### The privacy page

Written as plain sentences about what this build actually does, not legal
boilerplate — every claim is checkable against the code. It covers the
dictation disclosure Phase 8 explicitly deferred here: **Lists never records
audio and holds no microphone permission**, but the phone's speech app may
still send what you say to Google, and `EXTRA_PREFER_OFFLINE` is a request
rather than a guarantee. It says so, and says "if that matters to you, type
instead of speaking". The formal Play-store policy is still Phase 12's job.

### `/code-review high` — found 5, all fixed

1. **The export ran entirely on the main thread** — an N+1 checklist query
   loop, JSON serialisation and the file write, despite the KDoc claiming
   otherwise. Now `withContext(Dispatchers.IO)`.
2. **"Silent" was reported as "Sound + vibrate".** Below `IMPORTANCE_DEFAULT`
   a channel keeps its sound URI but Android never plays it, so reading the URI
   alone described a channel the user had explicitly silenced as noisy.
3. **The quick-time chips rendered the *defaults* for the first frames.** Not
   cosmetic: the chips are tappable, so someone who set "Soon" to 15 minutes
   could press a chip labelled "In 1 hour" that really did set an hour. They
   now render nothing until the stored settings have been read — no chip is
   better than a wrong one you can press.
4. **The Capture sheet's Alert style row never refreshed** after the user
   changed it through that row's own link. Fixed by sharing the Settings
   screen's resume-aware reader instead of having two copies.
5. **A nudge was queued for a notification that was never posted.** Without
   POST_NOTIFICATIONS the notifier returns early; `post()` reported success
   anyway, so the device would wake up to re-post nothing. `show()` now returns
   whether it posted.

### Verified on the emulator

Driven control by control, not just compiled:

- **Dark** repainted the whole app live, and the status-bar icons flipped to
  white — the emulator's system theme is light, so this also proved the
  `SystemBarStyle` re-apply works when the user's choice disagrees with the OS.
- **Colour from wallpaper** turned the entire app the wallpaper's blue and the
  support line changed to "On — following your wallpaper"; turning it back off
  restored the Lists palette.
- **Quick times**: set "This evening" to 8:30 pm; the sub-screen's live preview
  and then the **When editor's actual chip** both read "Tomorrow 8:30 pm"
  (tomorrow, because the emulator clock was 11:54 pm — the rollover rule
  working in the wild).
- **All-day** picker opened on 9:00 am, set to 10:00, row re-read "10:00 am".
- **Export** produced both files and handed them to the system share sheet.
  The plain-text export was read back off the device and contains due dates,
  repeat rules, places, notes, checklists and all-day flags; the JSON contains
  the same with ISO instants.
- **Nudge**: created a reminder due in two minutes, watched the notification
  post, and confirmed against `dumpsys alarm` that a
  `com.stackpointer.lists.REMINDER_NUDGE` alarm was registered for exactly
  +10 minutes. When it fired, the notification's `airtimeCount` went 1 → 2 (it
  really did alert again) and attempt 2 was queued for +10 more. The
  notification was then swiped away and the second attempt confirmed to post
  nothing — the "ignored" guard working.
- **Alert style** opened Android's own Reminders-channel page with the real
  switches on it. Setting the channel to **Silent** there and pressing back
  changed the row to read "Silent"; setting it back to Default changed it to
  "Sound + vibrate" again. That one round trip exercises both the resume-aware
  re-read and the importance fix from the code review at once — the old code
  would have said "Sound + vibrate" for a silenced channel and then never
  updated anyway.
- 107 unit tests pass (was 98).

### One ANR seen, investigated, not a defect

The emulator threw "Lists isn't responding" during the final reinstall. It was
checked rather than waved away, and it is the **same pattern Phase 8 already
recorded**: reason `No response to onStartJob` — i.e. WorkManager's boot/replace
re-schedule job, fired by `MY_PACKAGE_REPLACED` eight seconds after the install.
The ANR report shows system load **9.3**, 84% kernel time system-wide, `kswapd0`
at 39% and 1105 major page faults in our own process against 6.5% user time.
That is a cold-starting process being paged in on a thrashing machine, not app
code blocking. The app relaunched cleanly straight afterwards (6.7 s cold start
on the same loaded emulator) and everything above was then driven successfully.

Written down again because a future session that sees it should not go hunting
for a bug in the alarm code.

### Found while verifying, deliberately NOT fixed — needs a decision

**The When editor's "Early alert" row is still dead** — it shows "None" and a
chevron and does nothing. The Phase 8 audit missed it; it has been inert since
Phase 2. It is a genuine feature (design S07 lists it, and Samsung's Reminder
has it): "tell me 15 minutes before as well".

It was left alone rather than smuggled into this phase, because it is not a
settings problem — it needs a schema migration, a second alarm per reminder in
`AlarmPlanner` (which currently keys one alarm per reminder id), and
notification copy that distinguishes "in 15 minutes" from "now". That is real
work on the highest-risk subsystem in the app, and doing it in the last hour of
a large phase is how the alarm engine gets broken.

**Recommendation: give it its own slot before Phase 12**, alongside the Today
filter/overflow work already queued. Umang's call.

---

## Phases 10–12 — ⬜ NOT STARTED

See PLAN.md for full descriptions of each.

## Open product/technical TODOs carried from PLAN.md

- ~~Recycle-bin retention length~~ — **settled in Phase 9.** It is now a user
  setting (7/14/30/60 days, default 30), and the bin screen's own copy reads
  from it.
- Real app icon: still a placeholder.
- Design S05's full-bleed map of all geofences: deferred in Phase 7, needs the
  Maps SDK and a billable API key, which PLAN.md deliberately avoided. Worth an
  explicit decision before Phase 12.
- ~~Voice recognition privacy disclosure wording~~ — **written in Phase 9**, on
  Settings › About › Privacy. It says outright that Lists never records audio
  and holds no microphone permission, but that the phone's own speech app may
  send what you say to Google, and that `EXTRA_PREFER_OFFLINE` is a request
  rather than a guarantee.
- A full-screen photo viewer: attachments show as thumbnails only. Phase 12.
