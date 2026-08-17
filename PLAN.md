# Lists — full implementation plan

> This is the plan as approved before implementation started. It does not
> change as phases complete — see [PROGRESS.md](PROGRESS.md) for the current
> status and what to do next. Read [CLAUDE.md](CLAUDE.md) first if you
> haven't.

## Context

Umang wanted a from-scratch Android app called **Lists**, matching a
Material 3 Expressive design he built in Claude Design (20 screens, light +
dark, exact color/type/shape tokens), replicating the feature set of
Samsung's "Reminder" app. He is not an Android developer, so this plan is
written to be followed phase by phase, each one independently demoable by
opening Android Studio, pressing Run, and tapping through a short
verification script — no code reading required. The repo
`github.com/umangkaswala/Lists` (remote `origin`, branch `main`) is where all
work is pushed.

The feature list below is taken directly from reading every screen's spec in
the design file (`Lists - M3 Expressive.dc.html` + the 20 `S0xName.dc.html`
files), not invented — see CLAUDE.md for how to re-fetch those.

### Decisions that shape this plan

- **Local-only v1.** No accounts, no backend, no real sync. The design's
  "shared with 2 people" / sync-card UI gets built but wired to a "Coming
  soon" state — no server calls.
- **stack-pointer.com** is unrelated to this app for now.
- Built with **eventual Google Play publishing** in mind (permission UX,
  privacy-policy page, proper signing), even though not submitting yet.
- **Package ID:** `com.stackpointer.lists`. **App name:** "Lists".
- Testing happens on **both an emulator and Umang's own phone** (USB
  debugging) — phases involving location, notifications, and widgets behave
  meaningfully differently on an emulator vs. a real device, flag those.
- **Place search:** free, on-device `Geocoder` — no API key or billing
  account to manage. (Trade-off: no live autocomplete dropdown, just
  type-and-search. Upgradeable to Google Places API later if ever wanted.)
- **Git workflow:** commit and push straight to `main` after each phase is
  demoed and approved — tell Umang before every push, since pushing is a
  "visible to others" action. This is a standing pattern approval, not a
  blanket standing approval to push anytime for any reason.

### Other defaults picked, flagged for override if disagreed with

- `minSdk` 26 (Android 8.0) — needed for notification channels, Glance
  widgets, and cleaner exact-alarm APIs; covers the overwhelming majority of
  active devices.
- Single Gradle module, organized by feature folder (`home/`, `capture/`,
  `places/`, `widgets/`, ...) — simplest for a solo/AI-built project this
  size.
- Manual dependency injection (a small `AppContainer`) instead of Hilt — one
  less framework to reason about, fine at this app's scale.
- Recycle-bin auto-purge after **30 days** (the design doesn't specify a
  number).
- Placeholder app icon until real art is approved.
- Completed-history and "This week" widget bar charts are hand-drawn with
  Compose `Canvas` — no third-party charting library needed for two simple
  bar charts.

## Feature scope (from the design spec)

The design covers, screen by screen: onboarding & permissions; Home (search
bar, list tiles, Today tile, filter chips, grouped reminder cards, capture
pill); first-run empty state; Today filtered view with
swipe-to-complete/swipe-to-snooze; Places (geofenced, location-based
reminders, arrive/leave triggers); Capture (free-text entry with on-device
parsing of dates/times/repeats/places into editable chips, plus
When/Where/Checklist&List sub-editors); custom repeat rules (RRULE-based);
Reminder Detail (edit/snooze/share/delete, note, completion-streak history);
Search (title+note+checklist, recent searches); Completed (7-day chart,
on-time/late tracking, undo); Recycle Bin with multi-select; Lists management
(reorderable, colored, with a stubbed sharing/sync UI); Settings; real
Android notifications with Done/Snooze actions; and 7 home-screen widgets
plus a widget-configuration screen.

Voice capture (speech-to-text) and photo attachments are implied by the
Capture screen's action-row icons and are included in scope.

## Implementation approach — phased, each phase independently demoable

Sequenced by **risk and what later phases depend on**, not by screen number —
the trickiest, most foundational logic (recurrence math, text parsing, alarm
reliability, geofencing, widget plumbing) gets proven early in small,
testable pieces, before UI is built on top of it.

**Phase 0 — Project bootstrap.** Create the Android Studio project (Compose,
package `com.stackpointer.lists`), encode the exact color/type/shape tokens
into `ui/theme/{Color,Type,Shape,Theme}.kt`, empty `NavHost`, Gradle version
catalog, `.gitignore`, first commit. *Demo: app launches, correctly themed,
blank Home.*

**Phase 1 — Data model + Home (read) + Lists CRUD + Detail
(read/complete).** Room entities/DAOs/repositories (`ReminderEntity` with
soft-delete column, `ReminderListEntity`, `ChecklistItemEntity`,
`PlaceEntity` schema-only for now). Home screen (tiles, filter chips, grouped
cards), first-run empty state, Lists screen (reorder, create, stubbed
sharing card), Reminder Detail (property/note cards, Complete works). *Demo:
browse seeded sample data, complete a reminder, create/reorder lists.*

**Phase 2 — Capture create/edit + Capture-When.** Bottom-sheet Capture with
plain text entry and a working When sub-editor (date/time, hardcoded
quick-time chips for now). Wired into the capture pill, empty-state prompts,
and Detail's Edit action. *Demo: create and edit a reminder with a due
date/time end-to-end.*

**Phase 3 — Parser + RRULE engine (spike, tested in isolation first).**
Pure-Kotlin, on-device text parser (no cloud calls) for phrases like
"tomorrow 7pm every Monday"; RFC5545 RRULE serializer/parser/expander for
custom repeat rules, unit-tested against hand-computed dates including
DST/month-end edge cases, *before* wiring into UI. Custom Repeat screen.
Also: a tiny "Hello Widget" Glance smoke test to catch widget-toolchain
issues (manifest wiring, version alignment) this early rather than in Phase
10. *Demo: typed text auto-fills chips; a "repeat every 2 weeks on Tue/Fri"
reminder correctly reschedules itself after completion.*

**Phase 4 — Today view, swipe actions, Checklist & List capture, Search.**
Today screen with swipe-to-complete/swipe-to-snooze + undo; checklist item
editing; list picker with inline list creation; full-text Search across
title/note/checklist with recent searches. *Demo: checklist progress shows
on Home; swipe actions work with undo; search finds a reminder by note
text.*

**Phase 5 — Notifications & exact-alarm scheduling (physical-device-critical).**
Minimal one-off alarm → real high-priority notification, verified on a
physical phone (screen off, after Doze, after reboot) before adding
complexity. Then Done/Snooze actions, boot-reschedule via WorkManager, and
the Onboarding screen's permission requests go live for real (notifications,
exact-alarm settings intent). *Demo: set a reminder 2 minutes out, lock the
phone, get a real notification, snooze it, reboot and confirm it still
fires.*

**Phase 6 — Completed & Recycle Bin.** 7-day completion chart,
on-time/late tracking, completion-streak history on Detail, Recycle Bin with
multi-select and 30-day auto-purge. *Demo: complete reminders, view the
chart, delete and restore one, bulk-delete two.*

**Phase 7 — Places & geofencing (physical-device-critical).**
Single-geofence walk-test on a physical phone first, then the full Places
screen, arrive/leave-triggered notifications ("You just left Work"),
Capture-Where (Geocoder-based place search, radius slider, time window), and
the 90-warning/100-hard-limit on geofence count. *Demo: leave a saved
place's radius and get a real notification.*

**Phase 8 — Voice capture & photo attachment.** Speech-to-text feeding the
same parser from Phase 3; camera/gallery photo attachment stored locally.
*Demo: speak a reminder, watch it parse; attach a photo and see it on
Detail.*

**Phase 9 — Settings & onboarding polish.** Theme/dynamic-color toggles
made real, configurable quick-time presets (feeding back into Phase 2's When
editor), parsing disclosure copy, placeholder Privacy Policy page. Also:
swap in the real Roboto Flex font (deferred from Phase 0). *Demo: toggle
dark mode/dynamic color live; change quick-time presets and see them
reflected in Capture.*

**Phase 10 — Widgets, set A.** 4×2 list widget, 2×2 count, 2×2 quick-add,
built on the Phase-3 smoke-tested Glance plumbing. *Demo: complete a
reminder directly from a home-screen widget.*

**Phase 11 — Widgets, set B + widget configuration.** Next-up, count,
Near-you (depends on Phase 7 places), pinned-checklist, This-week chart,
voice, lock-screen glanceable, plus the widget config activity. *Demo:
configure the checklist widget to a specific list via its config screen;
confirm Near-you hides itself with no nearby places.*

**Phase 12 — Full polish & Play-readiness pass.** Light/dark parity audit
against the spec, empty/error-state audit, accessibility (content
descriptions, touch targets, a TalkBack spot-check), real Privacy Policy
copy, Play Data Safety answers drafted, final app icon, a combined
physical-device regression pass (notifications + geofencing + widgets
together, since that combination most often reveals issues invisible in
isolation).

## Critical files

- `app/build.gradle.kts` — version catalog gating every later phase
  (Compose, Room, Glance, WorkManager, Play Services Location).
- `data/db/ListsDatabase.kt`, `data/entity/*.kt` — Room schema everything
  else builds on; getting entity/foreign-key shape right early avoids
  painful migrations later (mitigated further by using
  `fallbackToDestructiveMigration` pre-launch, see PROGRESS.md).
- `recurrence/RRule*.kt` (Phase 3) — the RFC5545 engine Detail, Completed
  history, and Custom Repeat all depend on.
- `notifications/AlarmScheduler.kt`, `AlarmReceiver.kt`,
  `RescheduleAlarmsWorker.kt` (Phase 5) — the exact-alarm + boot-resilience
  trio; the single highest-risk reliability surface.
- `places/GeofenceRepository.kt`, `GeofenceBroadcastReceiver.kt` (Phase 7) —
  geofencing core that Places, alert copy, and the Near-you widget key off
  of.

## Open TODOs (flagged, not silently decided)

- Recycle-bin retention: defaulting to 30 days, easy to change later.
- App icon: placeholder until real art is approved (Phase 0, revisited
  Phase 12).
- Voice recognition may round-trip through Google's cloud speech service
  unless the device supports on-device recognition — worth disclosing in
  Settings/Privacy Policy copy (Phase 8/9), not a blocker now.

## Risk register (why phases are ordered this way)

| Risk | Where it's handled | Mitigation |
|---|---|---|
| RRULE math (DST, month-end, `UNTIL`/count edge cases) | Phase 3 | Pure-function engine, unit-tested against hand-computed outputs, before any UI depends on it. |
| On-device text parsing correctness/ambiguity | Phase 3 | Same treatment — unit tests against example phrases before wiring into live Capture UI. |
| Exact-alarm reliability across reboot/Doze | Phase 5 | Minimal one-off-alarm spike verified on a physical device before adding snooze/multi-action complexity; boot-reschedule via WorkManager, not raw receiver work. |
| Geofence 100-per-app limit / background location UX scrutiny | Phase 7 | Single-geofence physical walk-test before building the full Places screen and the 90-warning logic; hard-stop enforced at the platform limit. |
| Glance toolchain quirks (manifest wiring, version alignment) | Smoke-tested at end of Phase 3, well before Phase 10's real widget work | Cheap, isolated "Hello Widget" proves the plumbing early when a fix is a small diff. |
| Place search/autocomplete dependency choice | Decided up front (Geocoder, see Decisions above) | No key-management surface at all. |

## Verification

Each phase ends with a short tap-through script (given per phase above).
Beyond that, **the AI itself installs and drives the app on an emulator
before ever telling the user a phase is done** — see CLAUDE.md's "Dev
environment" section for exactly how (adb install/launch/screenshot/
uiautomator). Phases 5, 7, 10, and 11 specifically need a **physical phone**
in addition, since notifications, geofencing, and widgets behave
meaningfully differently on an emulator. After a phase's demo checks out,
commit and push it to `main`, telling the user before pushing.
