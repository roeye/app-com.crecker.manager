# CLAUDE.md

Android app (Kotlin, Jetpack Compose, Material 3, minSdk 26) – a generic host for managing "systems" (sites, servers…), each with its own actions. Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) first.

## Commands

Gradle needs a JDK – use Android Studio's bundled one:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The Gradle daemon must run on a full JDK 25 (`gradle/gradle-daemon-jvm.properties`). If the build fails with `jlink executable … does not exist`, a daemon started on a JRE (e.g. the VS Code Java extension's) is being reused – run `.\gradlew.bat --stop` and build again with `JAVA_HOME` set as above. When bumping the wrapper's `distributionUrl`, update `distributionSha256Sum` too (from `…-bin.zip.sha256` on services.gradle.org).

The built APK is `app/build/outputs/apk/debug/app-debug.apk` – install that one (`adb install -r -t …`). `app/build/intermediates/apk/debug/` is Android Studio's own build and is not updated by `gradlew`.

**On-device testing** (the user's phone over wireless adb): screenshots are black because of `FLAG_SECURE`, so read the UI with `adb shell uiautomator dump` (text + bounds) and tap with `input tap`. A fresh install starts locked – the user unlocks. In Git Bash set `MSYS_NO_PATHCONV=1` before adb paths like `/sdcard/…`. Restore anything changed for a test (thresholds, app language) afterwards.

Lint must pass with no errors (it fails the build). Notably: in composables read strings via `stringResource` / `LocalResources.current`, never `LocalContext.current.getString` (`LocalContextGetResourceValueCall`).

## Conventions

- **The app is bilingual (English + Hebrew).** Never hardcode user-visible text (UI, log messages, error messages, notifications): add it to `res/values/strings.xml` (English, default) **and** `res/values-iw/strings.xml` (Hebrew). Composables use `stringResource`; non-UI code uses `ActionContext.getString` / `context.localized()`; code without a Context throws `LocalizedException(R.string.x, args)`. Model labels are `@StringRes Int` (action titles, categories, statuses). Layout direction follows the chosen language – don't force RTL.
- Docs are in Hebrew. Code identifiers and KDoc tags in English; short comments may be Hebrew (match the surrounding file).
- A system = a folder under `systems/<name>/` + one line in `app/SystemRegistry.kt`. `core/` must not depend on any system.
- Prefer `SystemAction.Logged` (generic screen: description, run/cancel, live log, history). Use `Custom` only when the log template doesn't fit.
- Action/system `id`s are stable – settings and history are keyed by them.
- Settings have two levels: `SystemSettings` (prefixed `systemId/`, shared by all actions – e.g. server connection; edited in `ManagedSystem.settingsScreen`, available to actions as `ActionContext.systemSettings`) and `ActionSettings` (prefixed `systemId/actionId/` – schedule, thresholds, options). When moving existing keys between levels, add a `MoveToSystemSettings` to `ManagedSystem.settingsMigrations` – never lose saved user settings. Secrets go through `putSecret`/`getSecret` (Keystore-encrypted); never store secrets in plain prefs or code.
- Dates/elapsed time: `formatDateTime(instant, zone)` / `formatElapsed` (`core/model/TimeFormat.kt`); remote file timestamps via `RemoteUploader.fileInfo`.
- Temp files go in `ActionContext.workDir` (auto-deleted). Blocking IO inside actions: wrap in `runInterruptible`, call `currentCoroutineContext().ensureActive()` between steps, always rethrow `CancellationException`.
- Reuse `core/ui` building blocks (`SettingsScaffold`, `RemoteSettingsSection`, `ScheduleSection`, `LogView`) instead of new screens.
- No DI framework – shared singletons live in `AppGraph` (`context.graph`).
- `MainActivity` is an `AppCompatActivity` with an AppCompat theme – required for per-app language and night mode (`AppCompatDelegate`). App-wide preferences: `AppPreferences` (theme, app lock) and `AppLanguage` (language, persisted by the system/AppCompat).
- App lock: `AppLock` + `DeviceAuth` (`core/security`). Any setting that weakens security must go through `DeviceAuth.authenticate` with the default `requireConfirmation = true`; only the app-entry unlock (`LockScreen`) passes `false`, so face unlock enters without a "Confirm" tap. The activity uses `FLAG_SECURE` – keep it.
- Home status lines (`LastRunLine`): the status dot is top-aligned with the first line of the text (`Alignment.Top`, same text style), not centered – the text may wrap on narrow/portrait screens.
- App settings screen: settings are grouped by category (Security first, then Display & language); each setting has an icon and opens a `ChoiceDialog`.
- Icons: launcher icon is a vector adaptive icon (background / foreground / monochrome in `res/drawable`); notifications use `ic_notification` (single color). In-app logo: `AppLogo` / `LogoTitle`. Material icons come from `material-icons-extended`.
- Home screen exits only on a double back press within 1 s (`DoubleBackToExit`).
- Dependencies go through `gradle/libs.versions.toml` (the user sometimes bumps versions there themselves – keep their versions). R8 rules: `app/src/main/keepRules/rules.keep`.

## Documentation

Every system has `docs/systems/<name>.md` (actions, inputs/URLs, validation rules, settings, failure behavior) and a row in `README.md`. Update them in the same change as the code.
