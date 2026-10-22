# ארכיטקטורה

## הרעיון

האפליקציה היא **host גנרי**, וכל מערכת היא **תוסף** שמגדיר את עצמו ואת הפעולות שלו.
ה-host לא יודע כלום על מה שבתוך מערכת, ולכן מוסיפים מערכות ופעולות בלי לגעת בקוד קיים.

```
מסך בית (מערכות לפי קטגוריה)  →  מסך מערכת (רשימת פעולות)  →  מסך פעולה
                                                                 ├─ ⚙ הגדרות
                                                                 └─ ☰ היסטוריית הרצות → הרצה
```

## מבנה התיקיות

```
com.crecker.manager/
├── ManagerApp.kt          Application + AppGraph (settings, history, runner)
├── MainActivity.kt        Theme + RTL + AppNavHost
├── app/                   ה-host
│   ├── SystemRegistry.kt  רשימת כל המערכות
│   ├── HomeScreens.kt     מסך בית + מסך מערכת
│   └── AppNavHost.kt      ניווט
├── core/                  תשתית משותפת – לא תלויה באף מערכת
│   ├── model/             ManagedSystem, SystemAction, RunModels (לוג, סטטוס, ActionContext)
│   ├── run/               ActionRunner, RunHistory, ActionScheduler (+Worker), Notifications
│   ├── settings/          SettingsStore/ActionSettings (DataStore), SecretCipher (Keystore)
│   ├── net/               HttpFetcher
│   ├── remote/            RemoteUploader: FTP / FTPS / SFTP
│   └── ui/                LoggedActionScreen, LogView, טפסי הגדרות (חיבור מרוחק, תזמון)
└── systems/               מערכת = תיקייה
    └── paisresults/
```

## מושגים

### `ManagedSystem`
מערכת: `id` (יציב), `name`, `category` (לקיבוץ במסך הבית), `icon`, `actions`, ו-`StatusLine()` אופציונלי לשורת סטטוס במסך הבית.

### `SystemAction`
- **`Logged`**: הסוג העיקרי. המסך גנרי (הסבר, כפתור הרצה/ביטול, לוג חי, היסטוריה, העתקה/שיתוף). הפעולה כותבת רק `run: suspend ActionContext.() -> RunStatus`.
  - `options`: UI מעל כפתור ההרצה (למשל צ'קבוקס).
  - `settingsScreen`: מסך ההגדרות של הפעולה (כפתור ⚙).
  - `schedulable`: אפשר להריץ אוטומטית ברקע.
- **`Custom`**: מסך מותאם לחלוטין, לפעולות שלא מתאימות לתבנית של לוג.

### `ActionContext`, מה שפעולה מקבלת בריצה
- `log`: `info` / `success` / `warn` / `error` / `table(...)`. מוצג חי ונשמר להיסטוריה.
- `settings`: ההגדרות **של הפעולה הזו בלבד** (המפתחות מקבלים prefix של `systemId/actionId/`).
- `workDir`: תיקייה זמנית ייחודית להרצה. **נמחקת אוטומטית** בסוף, גם בכשל או בביטול.
- `trigger`: `MANUAL` או `SCHEDULED`.

### `ActionRunner`
- מריץ ב-scope של האפליקציה, כך שההרצה ממשיכה גם אם יוצאים מהמסך.
- מונע הרצה כפולה של אותה פעולה (גם ידנית מול מתוזמנת).
- ביטול: מבטל את ה-coroutine. פעולות צריכות לקרוא ל-`ensureActive()` בין שלבים ולעטוף IO חוסם ב-`runInterruptible`.
- חריגה שלא נתפסה הופכת ל-`FAILED` עם הודעה בלוג.

### היסטוריה
JSON תחת `files/run-history/<system>__<action>/`. נשמרות 20 הרצות אחרונות לכל פעולה.

### הגדרות וסודות
- DataStore (`settings.preferences_pb`).
- סודות (`putSecret`/`getSecret`) מוצפנים ב-AES-256-GCM עם מפתח ב-Android Keystore.
- קובץ ההגדרות **מוחרג מגיבוי** (`backup_rules.xml`, `data_extraction_rules.xml`), כי מפתח ה-Keystore לא עובר גיבוי ממילא.

### חיבור מרוחק (`core/remote`)
- `RemoteUploader.create(config)` מחזיר FTP, FTPS (explicit TLS) או SFTP.
- `uploadAtomic`: העלאה לשם `name.tmp` ואז rename, כך שהאתר לא קורא קובץ חצי מועלה. אם rename נכשל על קובץ קיים: מחיקה ואז rename.
- FTP במצב passive ובינארי. Timeout של 30 שניות.
- SFTP: מפתח השרת **לא נבדק** (`StrictHostKeyChecking=no`). לשיפור בעתיד: לשמור את טביעת האצבע בחיבור הראשון ולוודא אותה בהמשך (TOFU).
- טופס גנרי `RemoteSettingsSection` כולל בחירת פרוטוקול, פורט (ריק = ברירת מחדל), סיסמה מוצפנת ו**בדיקת חיבור**.

### תזמון
- `ScheduleSection` (טופס) ו-`ActionScheduler` (WorkManager periodic, מינימום 15 דקות, רק כשיש רשת).
- `ScheduledActionWorker` מריץ את הפעולה דרך אותו `ActionRunner`, ושולח התראה אם ההרצה לא הצליחה במלואה (ניתן לכבות).
- אנדרואיד עשוי לדחות הרצות (Doze), כך שזה לא תזמון מדויק לדקה.

## איך מוסיפים מערכת חדשה

1. יוצרים תיקייה `systems/<name>/`.
2. יוצרים `object MySystem : ManagedSystem` עם `id` יציב, שם, קטגוריה ואייקון.
3. מוסיפים את הפעולות (ראו למטה).
4. מוסיפים `MySystem` ל-`SystemRegistry.systems`.
5. מתעדים ב-`docs/systems/<name>.md` ומוסיפים שורה לטבלה ב-`README.md`.

## איך מוסיפים פעולה

```kotlin
object MyAction {
    const val ID = "my_action"          // יציב! משמש להגדרות ולהיסטוריה

    val action = SystemAction.Logged(
        id = ID,
        title = "שם הפעולה",
        icon = Icons.Default.Build,
        description = "מה הפעולה עושה",
        settingsScreen = { settings, onBack -> MySettingsScreen(settings, onBack) }, // אופציונלי
        schedulable = true,                                                         // אופציונלי
        run = {
            log.info("מתחיל…")
            // ... לוגיקה; קבצים זמניים ב-workDir
            RunStatus.SUCCESS
        },
    )
}
```

מסך הגדרות עם חיבור מרוחק ותזמון נבנה מהרכיבים הקיימים:

```kotlin
val remote = rememberRemoteFormState(settings, defaultDir = "/path")
val schedule = rememberScheduleFormState(settings)
SettingsScaffold(title, loaded = remote.loaded && schedule.loaded, onBack,
    onSave = { remote.validationError() ?: run { remote.save(); schedule.saveAndApply(context); null } }) {
    RemoteSettingsSection(remote)
    ScheduleSection(schedule)
}
```

ובזמן ריצה: `RemoteSettings.load(settings, defaultDir)` ואז `RemoteUploader.create(config)`.

## כללים

- **`id` של מערכת ושל פעולה לא משתנים** אחרי שיש נתונים שמורים, כי ההגדרות וההיסטוריה מקושרות אליהם.
- `core/` לא מכיר אף מערכת (חוץ מ-`ScheduledActionWorker` שמחפש ב-`SystemRegistry`).
- טקסט UI ולוג בעברית.
