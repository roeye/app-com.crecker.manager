# CLAUDE.md

Android app (Kotlin, Jetpack Compose, Material 3, minSdk 26) – a generic host for managing "systems" (sites, servers…), each with its own actions. Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) first.

## Commands

Gradle needs a JDK – use Android Studio's bundled one:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

## Conventions

- **UI text, log messages and docs are in Hebrew.** Code identifiers and KDoc tags in English; short comments may be Hebrew (match the surrounding file). The app forces RTL.
- A system = a folder under `systems/<name>/` + one line in `app/SystemRegistry.kt`. `core/` must not depend on any system.
- Prefer `SystemAction.Logged` (generic screen: description, run/cancel, live log, history). Use `Custom` only when the log template doesn't fit.
- Action/system `id`s are stable – settings and history are keyed by them.
- Settings are per action (`ActionSettings`, prefixed `systemId/actionId/`). Secrets go through `putSecret`/`getSecret` (Keystore-encrypted); never store secrets in plain prefs or code.
- Temp files go in `ActionContext.workDir` (auto-deleted). Blocking IO inside actions: wrap in `runInterruptible`, call `currentCoroutineContext().ensureActive()` between steps, always rethrow `CancellationException`.
- Reuse `core/ui` building blocks (`SettingsScaffold`, `RemoteSettingsSection`, `ScheduleSection`, `LogView`) instead of new screens.
- No DI framework – shared singletons live in `AppGraph` (`context.graph`).
- Dependencies go through `gradle/libs.versions.toml`. R8 rules: `app/src/main/keepRules/rules.keep`.

## Documentation

Every system has `docs/systems/<name>.md` (actions, inputs/URLs, validation rules, settings, failure behavior) and a row in `README.md`. Update them in the same change as the code.
