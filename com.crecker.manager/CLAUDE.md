# CLAUDE.md

Android app (Kotlin, Jetpack Compose, Material 3, minSdk 26) – a generic host for managing "systems" (sites, servers…), each with its own actions. Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) first.

## Commands

Gradle needs a JDK – use Android Studio's bundled one:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Lint must pass with no errors (it fails the build). Notably: in composables read strings via `stringResource` / `LocalResources.current`, never `LocalContext.current.getString` (`LocalContextGetResourceValueCall`).

## Conventions

- **The app is bilingual (English + Hebrew).** Never hardcode user-visible text (UI, log messages, error messages, notifications): add it to `res/values/strings.xml` (English, default) **and** `res/values-iw/strings.xml` (Hebrew). Composables use `stringResource`; non-UI code uses `ActionContext.getString` / `context.localized()`; code without a Context throws `LocalizedException(R.string.x, args)`. Model labels are `@StringRes Int` (action titles, categories, statuses). Layout direction follows the chosen language – don't force RTL.
- Docs are in Hebrew. Code identifiers and KDoc tags in English; short comments may be Hebrew (match the surrounding file).
- A system = a folder under `systems/<name>/` + one line in `app/SystemRegistry.kt`. `core/` must not depend on any system.
- Prefer `SystemAction.Logged` (generic screen: description, run/cancel, live log, history). Use `Custom` only when the log template doesn't fit.
- Action/system `id`s are stable – settings and history are keyed by them.
- Settings are per action (`ActionSettings`, prefixed `systemId/actionId/`). Secrets go through `putSecret`/`getSecret` (Keystore-encrypted); never store secrets in plain prefs or code.
- Temp files go in `ActionContext.workDir` (auto-deleted). Blocking IO inside actions: wrap in `runInterruptible`, call `currentCoroutineContext().ensureActive()` between steps, always rethrow `CancellationException`.
- Reuse `core/ui` building blocks (`SettingsScaffold`, `RemoteSettingsSection`, `ScheduleSection`, `LogView`) instead of new screens.
- No DI framework – shared singletons live in `AppGraph` (`context.graph`).
- `MainActivity` is an `AppCompatActivity` with an AppCompat theme – required for per-app language and night mode (`AppCompatDelegate`). App-wide preferences: `AppPreferences` (theme, app lock) and `AppLanguage` (language, persisted by the system/AppCompat).
- App lock: `AppLock` + `DeviceAuth` (`core/security`). Any setting that weakens security must go through `DeviceAuth.authenticate`. The activity uses `FLAG_SECURE` – keep it.
- App settings screen: settings are grouped by category (Security first, then Display & language); each setting has an icon and opens a `ChoiceDialog`.
- Icons: launcher icon is a vector adaptive icon (background / foreground / monochrome in `res/drawable`); notifications use `ic_notification` (single color). In-app logo: `AppLogo` / `LogoTitle`. Material icons come from `material-icons-extended`.
- Home screen exits only on a double back press within 1 s (`DoubleBackToExit`).
- Dependencies go through `gradle/libs.versions.toml` (the user sometimes bumps versions there themselves – keep their versions). R8 rules: `app/src/main/keepRules/rules.keep`.

## Documentation

Every system has `docs/systems/<name>.md` (actions, inputs/URLs, validation rules, settings, failure behavior) and a row in `README.md`. Update them in the same change as the code.
