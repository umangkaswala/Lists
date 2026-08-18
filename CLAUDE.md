# Lists — project instructions for Claude

Read this file fully before doing any work here. It exists so a **brand-new chat
session** can pick up exactly where the last one left off, without the user
re-explaining anything.

## What this project is

"Lists" is a from-scratch Android app replicating Samsung's "Reminder" app,
built to match a 20-screen Material 3 Expressive design the user created in
Claude Design. The user (Umang) is **not an Android developer** — explain
things in plain language, report progress in terms he can verify himself by
tapping through the running app, and ask before making product/technical
trade-off decisions rather than assuming.

## Where the plan and progress live

- **[PLAN.md](PLAN.md)** — the full phase-by-phase implementation plan (Phase
  0 through Phase 12), the context/decisions behind it, and the risk register.
  This does not change as work progresses.
- **[PROGRESS.md](PROGRESS.md)** — the living record of what's actually been
  built, verified, and pushed so far, plus known issues and deferred items.
  **Read this first** — it says exactly which phase to resume at.

## Per-phase workflow

1. **Start of session**: read PROGRESS.md to find the current phase, then
   read that phase's entry in PLAN.md, then continue the work. Don't ask the
   user to re-describe the project — it's all in these files.
   **PROGRESS.md's "🔴 Outstanding bugs" section comes before any new phase** —
   those are confirmed defects the user has already run into, and shipping more
   features on top of them is the wrong order of work.
2. **Implement** the phase per its PLAN.md description.
3. **Self-verify on the emulator** before claiming it's done — install,
   launch, tap through it, screenshot (see "Self-verifying a phase" below).
   Compiling is not verifying.
4. **Run the `/code-review` skill on the diff** before committing — cheap
   insurance against bugs slipping through step 3, especially worth it on
   the riskier phases (5 alarms, 7 geofencing, 10/11 widgets). Fix anything
   it finds, or note in PROGRESS.md why a finding was skipped.
5. **Update PROGRESS.md** with what was done, how it was verified (including
   the code-review pass), what went well/didn't, and what's next.
6. **Commit and push to `main`** — tell the user before pushing.

This is the mechanism that makes fresh sessions cheap: a new session reads a
couple of short files instead of replaying a long chat history.

## Agents & tools for this project

These are built-in to Claude Code already — nothing to install, just a
policy for when to reach for each one on this project specifically:

- **`/code-review` skill** — standing step 4 of the per-phase workflow
  above. Always run it on the diff before committing, every phase.
- **Plan agent** (via the Agent tool) — use before starting the genuinely
  novel/risky phases: **5** (exact alarms), **7** (geofencing), **10/11**
  (widgets). These are the phases most likely to hit a non-obvious Android
  platform gotcha, so it's worth pressure-testing the approach before
  writing code. Skip it for routine phases (2, 4, 6, 8, 9) — they're
  already well-specified in PLAN.md and don't need a re-plan.
- **Explore agent** (via the Agent tool) — optional, for a broad "how is X
  wired across the codebase" audit if a question spans many files. Not
  needed for the normal flow of implementing one phase at a time.

Default to **not** spawning an agent otherwise — for a linear, sequential,
already-planned project like this one, the context-handoff cost usually
outweighs the benefit. Implement and self-verify directly, as in Phases 0–1.

## Key decisions already made (don't re-litigate these)

- **Local-only v1**: no accounts, no backend, no real sync. "Shared with" /
  sync UI is built but stubbed as "Coming soon".
- Package ID `com.stackpointer.lists`, app name "Lists". The stack-pointer.com
  domain is unrelated to this app.
- Built with eventual Google Play publishing in mind, not submitting yet.
- Place search uses free on-device `Geocoder`, not the paid Google Places API.
- **Git workflow**: commit and push straight to `main` after a phase is built
  and verified — tell the user before every push (see PLAN.md for the full
  reasoning). Never force-push, never skip hooks.
- minSdk 26, single Gradle module organized by feature package, manual DI via
  `AppContainer`/`ListsApplication` (no Hilt), recycle-bin retention 30 days
  (default, not user-specified), placeholder app icon until real art exists.

## Design source

The UI design lives in a claude.ai/design project named "Lists_03",
`projectId = 39f9c871-3993-4320-bacf-7de14586b123`. Read it with the
`DesignSync` tool (`list_files` / `get_file` — read-only, no permission
prompt). `Lists - M3 Expressive.dc.html` is the index with every screen's
full functional spec inline; `S01Onboarding.dc.html` through
`S20WidgetPicker.dc.html` are the individual screens. Fetch the specific
screen file for the phase you're implementing rather than relying on memory
of the summary.

## Tech stack & pinned versions

Kotlin **2.3.10** / KSP **2.3.10** (must stay matched — KSP hadn't published
a build for newer Kotlin as of this writing), AGP **9.3.1** (AGP 9+ has
**built-in Kotlin support** — do **not** apply
`org.jetbrains.kotlin.android`, it will fail to apply), Gradle **9.7.0**,
compileSdk/targetSdk **37**, minSdk **26**, Compose BOM **2026.08.00**, Room
**2.8.4**. Versions are in `gradle/libs.versions.toml` — check Google's/Maven
Central's metadata for newer *stable* (non-alpha/beta/rc) releases before
bumping anything, and re-verify Kotlin/KSP stay matched.

## Dev environment on this machine

- No system-wide `java`/`gradle` on PATH. Use Android Studio's bundled JBR as
  `JAVA_HOME`: `C:\Program Files\Android\Android Studio\jbr`. Gradle wrapper
  distributions are already cached under
  `C:\Users\Umang Kaswala\.gradle\wrapper\dists`.
- Android SDK: `C:\Users\Umang Kaswala\AppData\Local\Android\Sdk`.
  `local.properties` (gitignored) points `sdk.dir` there — recreate it if
  missing.
- Emulators available: `Pixel_9`, `Pixel_9_Pro`, `Medium_Phone_API_36.1`
  (`emulator -list-avds` to confirm current list).

### Build/run from Bash

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew.bat :app:assembleDebug --no-daemon
./gradlew.bat :app:testDebugUnitTest --no-daemon   # pure-Kotlin unit tests
```

(If the same script also sets `MSYS_NO_PATHCONV=1`, use the Windows-form
`JAVA_HOME` instead — see the gotchas list below.)

### Self-verifying a phase before telling the user it's done

Don't just compile — install and drive the app, the same way a human would,
before reporting a phase as finished:

```bash
export MSYS_NO_PATHCONV=1   # needed for /sdcard/... paths in Git Bash
ADB="/c/Users/Umang Kaswala/AppData/Local/Android/Sdk/platform-tools/adb.exe"
EMU="/c/Users/Umang Kaswala/AppData/Local/Android/Sdk/emulator/emulator.exe"

# boot an emulator if none is running (adb devices -l to check first)
nohup "$EMU" -avd Pixel_9 -no-snapshot > /tmp/emulator_boot.log 2>&1 &
"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done

"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell am start -n com.stackpointer.lists/.MainActivity
"$ADB" exec-out screencap -p > /tmp/screenshot.png   # then Read the PNG to actually look at it

# to find exact tap coordinates instead of guessing from a screenshot:
"$ADB" shell uiautomator dump /sdcard/window_dump.xml
"$ADB" pull /sdcard/window_dump.xml /tmp/dump.xml     # then grep for bounds="[x1,y1][x2,y2]"
```

Screenshot coordinates as *displayed* in chat are scaled down — always get
real tap coordinates from the `uiautomator dump` bounds, not by eyeballing
the resized screenshot image.

## Known gotchas hit so far (avoid re-discovering these)

- AGP 9's built-in Kotlin support means `org.jetbrains.kotlin.android` must
  **not** be applied as a plugin (only `kotlin.plugin.compose` and `ksp` are
  needed) — and there's no `kotlinOptions {}` DSL anymore; JVM target comes
  from `compileOptions`.
- `by viewModel.uiState.collectAsStateWithLifecycle()` (and any `by
  remember { mutableStateOf(...) }`) silently fails to compile without an
  explicit `import androidx.compose.runtime.getValue` — a very easy miss.
- `enableEdgeToEdge()` draws content under the system status/navigation bars
  with **no automatic inset padding**. Any custom top bar (not a stock
  `TopAppBar`) needs an explicit `.statusBarsPadding()`/`.systemBarsPadding()`
  or its top buttons become genuinely untappable in the top ~40px, not just
  visually cramped. This bit Home, Lists, and Detail in Phase 1 — check new
  screens for it too.
- Reading a value off a `by`-delegated Compose `State` twice (e.g. a
  null-check then a use) can fail smart-cast — assign to a local `val` first.
- `export MSYS_NO_PATHCONV=1` (needed for `/sdcard/...` adb paths) also stops
  Git Bash rewriting `JAVA_HOME` into a Windows path, so `gradlew.bat` then
  fails with "JAVA_HOME is set to an invalid directory". In any script that
  sets `MSYS_NO_PATHCONV`, write `JAVA_HOME` in Windows form instead:
  `export JAVA_HOME="C:\\Program Files\\Android\\Android Studio\\jbr"`.
- Room's `fallbackToDestructiveMigration` recreates the tables **without**
  calling `onCreate`, so seed data added there silently disappears after a
  schema-version bump — override `onDestructiveMigration` as well (see
  `data/db/ListsDatabase.kt`).
- In a `Row`, putting `Modifier.weight(1f)` on the *label* and leaving the
  value unweighted collapses the label to one character per line as soon as
  the value gets long. Weight the value instead. This bit Detail's property
  rows in Phase 3 once the Repeat row started showing a full rule summary.
- Running two Gradle builds against this project at once (e.g. two subagents)
  races on `app/build/` — expect spurious "Unresolved reference" errors and
  overwritten test-result XML. Retry rather than chasing the error; better,
  don't hand concurrent agents tasks that both need Gradle.
- **Subagents cannot use the `DesignSync` tool** — it's only wired into the
  main session. Two Phase 4 agents independently discovered this and had to
  guess at the design. When delegating a *screen*, fetch its `.dc.html` spec
  yourself first and paste the relevant details into the agent's prompt, then
  check the result against the spec when it comes back.
- Room's `fallbackToDestructiveMigration` aside, remember `@Transaction` on
  any DAO method that deletes-then-inserts (see `ChecklistItemDao`): the
  Capture sheet's coroutine scope dies when the sheet closes, so a
  non-transactional save can commit the delete and lose the inserts.
- SQLite `LIKE` treats a user-typed `%` or `_` as a wildcard. Any search query
  must go through `ReminderRepository.escapeForLike` and the SQL needs
  `ESCAPE '\'`, or searching "50%" returns the entire table.
- Don't index a string with offsets taken from `text.lowercase()` —
  lowercasing can change length (Turkish İ) and the offsets then run past the
  end. Use `indexOf(..., ignoreCase = true)` on the original string.
- **A `PendingIntent`'s identity ignores its extras.** Two alarms built as
  `Intent(ctx, Receiver::class)` + `putExtra(id)` with the same request code
  are *the same* PendingIntent, so each new reminder silently replaces the
  previous one — the classic "only my newest reminder ever fires". Give each a
  distinct `data` URI (`lists://reminder/$id`) as well as a distinct request
  code. See `AlarmScheduler.alarmPendingIntent`.
- **A notification channel is immutable once created.** Importance, sound and
  vibration can never be changed by the app afterwards, only by the user —
  changing them in code needs a *new channel id*, which resets the user's own
  tweaks. Settle them before shipping.
- **Receivers for system broadcasts need `android:exported="true"`**
  (`BOOT_COMPLETED` and friends come from another UID). Exported="false" means
  they simply never fire, with nothing in the log to say why. Receivers
  triggered only by our own PendingIntents stay `exported="false"`.
- `onReceive` runs on the main thread and Room refuses main-thread queries, so
  any DB work needs `goAsync()` — and `pendingResult.finish()` must run on
  every path or the device is held awake.
- Anything kicked off from a Compose scope that outlives the screen must run on
  `AppContainer.applicationScope`. `rememberCoroutineScope()` is cancelled when
  the composition goes away, so a `popUpTo(inclusive)` navigation can kill a
  DataStore write mid-flight (this bit onboarding's "don't show again" flag).
- Driving the Capture sheet over adb: tapping the pill opens the sheet but does
  **not** focus its text field. Tap the pill, then tap the `EditText` bounds,
  *then* `input text` — otherwise the text goes to whatever had focus before.

## What NOT to do without asking

Per the user's standing instructions: don't assume on open product/technical
trade-offs (see PLAN.md's "Open TODOs" section for known ones), don't push to
`main` without telling the user first, and don't skip a phase's own
verification step even though the user isn't watching in real time.
