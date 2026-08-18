# Progress

> Read this file first in every new session — it says what's done and what
> to do next. Full phase descriptions are in [PLAN.md](PLAN.md); working
> conventions and environment setup are in [CLAUDE.md](CLAUDE.md).

**Current status: Phase 5 built, emulator-verified and code-reviewed.
Awaiting Umang's physical-device pass. Start Phase 6 next.**

Phase 5's remaining verification needs **Umang's own phone** — real Doze, real
Samsung battery management, and a reboot with a secure lock screen. See the
"Still needs the phone" list in the Phase 5 entry below.

## 🔴 Outstanding bugs — fix these before starting a new phase

These are confirmed defects against the design, not ideas. Do them first.

### 1. The When editor has no way to set a date or a time (design S07)

**Found by Umang on 2026-08-18, testing the Phase 5 build on his own phone.**
He opened **When**, looked for a clock, and there wasn't one. His words: *"I
don't know how do i set the time for the reminder."*

Today the only ways to give a reminder an arbitrary time are the four quick-pick
chips, or typing the time into the reminder text and letting the parser catch it
(`in 20 minutes`, `14:35`, `at 7pm`). Neither is discoverable, and a reminder app
where you can't pick a time is not finished.

**This is a straight spec deviation, not a design decision.** `S07When.dc.html`
specifies, directly under the All day toggle and above the quick chips:

- a **Date** field — label "Date", value e.g. "Tue, 19 Aug"
- a **Time** field — label "Time", value e.g. "7:00 pm"

Both are 56dp tall, `secondaryContainer`, 16dp corners, sitting side by side in a
row (Date takes the remaining width, Time is a fixed 140dp), each showing a small
11sp label above a 16sp semibold value. Tapping them opens the M3 date and time
pickers. I built the chips and silently dropped both fields.

**Second, smaller deviation on the same screen:** the design's quick chips are
**"In 1 hour" / "Tonight 7 pm" / "Tomorrow 9 am"** (three, outlined, showing the
actual resulting time). Mine are "Later today / Tonight / Tomorrow / Next week"
(four, no times shown). Fix both together.

**Where the code lives:** `capture/CaptureSheet.kt` (the When sub-editor) and
`capture/QuickTimePresets.kt`. A working M3 `DatePicker` already exists in
`repeat/RepeatEditor.kt`'s "On a date" option — copy that pattern, and pair it
with `TimePicker`. Phase 9 later makes the quick presets configurable in
Settings, so keep them in one place.

**Why it went unnoticed for three phases:** every check we run is aimed
elsewhere. `/code-review` looks for correctness bugs, and the code is correct.
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
> top of this file, which carries the full spec and the fix.

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

## Phase 5 — Notifications, exact alarms, onboarding permissions — ✅ BUILT (2026-08-18)

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

## Phases 6–12 — ⬜ NOT STARTED

See PLAN.md for full descriptions of each.

## Open product/technical TODOs carried from PLAN.md

- Recycle-bin retention length: defaulted to 30 days, not user-specified.
- Real app icon: still a placeholder.
- Voice recognition privacy disclosure wording: needed by Phase 8/9, not
  written yet.
