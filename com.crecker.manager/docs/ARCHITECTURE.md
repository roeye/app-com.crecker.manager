# ארכיטקטורה

## הרעיון

האפליקציה היא **host גנרי**, וכל מערכת היא **תוסף** שמגדיר את עצמו ואת הפעולות שלו.
ה-host לא יודע כלום על מה שבתוך מערכת, ולכן מוסיפים מערכות ופעולות בלי לגעת בקוד קיים.

```
[מסך נעילה – אם הנעילה פעילה]
        ↓
מסך בית (מערכות לפי קטגוריה)
   ├─ ⚙ הגדרות האפליקציה
   ├─ "חזרה" ×2 תוך שנייה → יציאה
   └─ מסך מערכת (רשימת פעולות)
        ├─ ⚙ הגדרות המערכת (משותפות לכל הפעולות, למשל חיבור לשרת)
        └─ מסך פעולה
             ├─ ⚙ הגדרות הפעולה
             └─ ☰ היסטוריית הרצות → הרצה
```

## מבנה התיקיות

```
com.crecker.manager/
├── ManagerApp.kt          Application + AppGraph (appPreferences, appLock, settings, history, runner)
├── MainActivity.kt        AppCompatActivity + Theme + AppNavHost + שכבת נעילה + FLAG_SECURE
├── app/                   ה-host
│   ├── SystemRegistry.kt  רשימת כל המערכות
│   ├── HomeScreens.kt     מסך בית (כולל "חזרה" כפולה ליציאה) + מסך מערכת
│   ├── AppSettingsScreen.kt  הגדרות האפליקציה: אבטחה (נעילה), תצוגה ושפה
│   ├── LockScreen.kt      מסך הנעילה
│   ├── AppLogo.kt         AppLogo / LogoTitle – הלוגו בכותרות ובמסך הנעילה
│   └── AppNavHost.kt      ניווט
├── core/                  תשתית משותפת – לא תלויה באף מערכת
│   ├── model/             ManagedSystem, SystemAction, RunModels (לוג, סטטוס, ActionContext),
│   │                      TimeFormat (תאריך לפי אזור זמן, ISRAEL_ZONE, "לפני X"), SizeFormat
│   ├── run/               ActionRunner, RunHistory, ActionScheduler (+Worker), Notifications
│   ├── security/          AppLock (מצב נעילה), DeviceAuth (BiometricPrompt)
│   ├── settings/          SettingsStore, SystemSettings/ActionSettings (DataStore), MoveToSystemSettings,
│   │                      SecretCipher (Keystore),
│   │                      AppPreferences / ThemeMode / AppLanguage, Context.localized()
│   ├── net/               HttpFetcher, HttpText (פענוח תשובה לטקסט, HTML → טקסט, זיהוי שגיאות PHP)
│   ├── remote/            RemoteUploader: FTP / FTPS / SFTP (העלאה + fileInfo), loadSystemRemote,
│   │                      LastUpdateCheck (פעולה גנרית: "בדיקת עדכון אחרון" של קבצים בשרת)
│   └── ui/                LoggedActionScreen, LogView, טפסי הגדרות (חיבור מרוחק, תזמון),
│                          RemoteSystemSettingsScreen (מסך הגדרות מערכת עם חיבור לשרת בלבד)
└── systems/               מערכת = תיקייה
    ├── paisresults/       cron data retrieval + בדיקת עדכון אחרון
    ├── flightstatus/      cron data retrieval (הרצת סקריפט בשרת) + בדיקת עדכון אחרון (קובץ אחד)
    └── mail/              תיבות דואר (IMAP, קריאה בלבד): ImapClient, MailParser, MailStore (מטמון)
```

## מושגים

### `ManagedSystem`
מערכת: `id` (יציב), `name` (שם פרטי, לא מתורגם), `description` ו-`category` (`@StringRes`), `icon`, `actions`, ו-`StatusLine()` אופציונלי לשורת סטטוס במסך הבית.

אופציונלי:
- `settingsScreen`: מסך **הגדרות המערכת** (כפתור ⚙ במסך המערכת), להגדרות שמשותפות לכל הפעולות, למשל פרטי החיבור לשרת.
- `settingsMigrations`: העברות של הגדרות שמורות (ראו "הגדרות וסודות").
- `LastRunLine(systemId, actionId, label)` בתוך `StatusLine()`: שורת "ריצה אחרונה" עם נקודה צבעונית לפי הסטטוס. כשמציגים כמה פעולות, `label` הוא שם קצר של הפעולה. אם הטקסט נשבר לכמה שורות (למשל במסך לאורך), הנקודה נשארת **בגובה השורה הראשונה** ולא ממורכזת לכל הטקסט (`Alignment.Top`, ולנקודה ולטקסט אותו style).

### `SystemAction`
- `title` (ו-`description` ב-`Logged`) הם `@StringRes`.
- **`Logged`**: הסוג העיקרי. המסך גנרי (הסבר, כפתור הרצה/ביטול, לוג חי, היסטוריה, העתקה/שיתוף). הפעולה כותבת רק `run: suspend ActionContext.() -> RunStatus`.
  - `options`: UI מעל כפתור ההרצה (למשל צ'קבוקס).
  - `settingsScreen`: מסך ההגדרות של הפעולה (כפתור ⚙).
  - `schedulable`: אפשר להריץ אוטומטית ברקע.
- **`Custom`**: מסך מותאם לחלוטין, לפעולות שלא מתאימות לתבנית של לוג (למשל תיבת הדואר ב-`mail`). אם צריך כמה מסכים, למשל רשימה ופריט, הניווט ביניהם נעשה בתוך המסך עצמו: state עם `rememberSaveable` ו-`BackHandler`.

### `ActionContext`, מה שפעולה מקבלת בריצה
- `log`: `info` / `success` / `warn` / `error` / `table(...)` / `output(title, text)`. מוצג חי ונשמר להיסטוריה.
  - `output`: בלוק של פלט גולמי מרובה שורות, למשל הפלט של סקריפט בשרת. מוצג בגופן קבוע, בלי שבירת שורות ועם גלילה אופקית. נשמר בהיסטוריה (`LogEntry.output`) ונכלל בהעתקה ובשיתוף.
- `settings`: ההגדרות **של הפעולה הזו בלבד** (המפתחות מקבלים prefix של `systemId/actionId/`).
- `systemSettings`: ההגדרות **של המערכת**, משותפות לכל הפעולות שלה (prefix של `systemId/`).
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
- DataStore (`settings.preferences_pb`), מופע יחיד ב-`AppGraph`.
- שתי רמות, עם אותו API (`ScopedSettings`):
  - **`SystemSettings`** (`store.forSystem(id)`): הגדרות שמשותפות לכל הפעולות של המערכת, כמו פרטי חיבור לשרת. נערכות במסך הגדרות המערכת.
  - **`ActionSettings`** (`store.forAction(systemId, actionId)`): הגדרות של פעולה אחת, כמו תזמון, ספים ואפשרויות.
- **העברת הגדרות:** כשהגדרה של פעולה הופכת למשותפת, המערכת מצהירה על `MoveToSystemSettings(systemId, actionId, keys)` ב-`settingsMigrations`. ההעברה רצה **פעם אחת, לפני הקריאה הראשונה** מה-DataStore (DataMigration), כך שאין מצב שבו פעולה, כולל מתוזמנת, קוראת לפני שההעברה הסתיימה. ערך שכבר קיים ברמת המערכת לא נדרס. סודות מועתקים מוצפנים כמו שהם, כי המפתח זהה.
- סודות (`putSecret`/`getSecret`) מוצפנים ב-AES-256-GCM עם מפתח ב-Android Keystore.
- קובץ ההגדרות **מוחרג מגיבוי** (`backup_rules.xml`, `data_extraction_rules.xml`), כי מפתח ה-Keystore לא עובר גיבוי ממילא. גם מטמון שיש בו מידע פרטי מוחרג, למשל `mail-cache/`.
- **רשימות** (למשל כמה תיבות דואר): נשמרות כ-JSON במפתח אחד, ולכל פריט יש `id` קבוע. הסוד של כל פריט נשמר במפתח נפרד, למשל `password_<id>`.

### חיבור מרוחק (`core/remote`)
- `RemoteUploader.create(config)` מחזיר FTP, FTPS (explicit TLS) או SFTP.
- `uploadAtomic`: העלאה לשם `name.tmp` ואז rename, כך שהאתר לא קורא קובץ חצי מועלה. אם rename נכשל על קובץ קיים: מחיקה ואז rename.
- `fileInfo(path)`: זמן השינוי האחרון (ב-UTC) וגודל של קובץ, או `null` אם הקובץ לא קיים. ב-FTP: `MDTM`, ואם השרת לא תומך בו, `MLST`. הגודל מגיע מ-`SIZE`. ב-SFTP: `stat`.
- `RemoteConfig.remotePath(name)`: נתיב מלא לקובץ בתוך תיקיית היעד.
- FTP במצב passive ובינארי. Timeout של 30 שניות.
- SFTP: מפתח השרת **לא נבדק** (`StrictHostKeyChecking=no`). לשיפור בעתיד: לשמור את טביעת האצבע בחיבור הראשון ולוודא אותה בהמשך (TOFU).
- טופס גנרי `RemoteSettingsSection` כולל בחירת פרוטוקול, פורט (ריק = ברירת מחדל), סיסמה מוצפנת ו**בדיקת חיבור**. הוא עובד גם עם `SystemSettings` וגם עם `ActionSettings`. אם כמה פעולות מתחברות לאותו שרת, ההגדרות שייכות למערכת.
- **`RemoteSystemSettingsScreen(systemName, settings, defaultDir, onBack)`**: מסך הגדרות מערכת מוכן שכולל רק את החיבור לשרת. משמש את המערכות של האתרים (`ManagedSystem.settingsScreen`). לכל מערכת יש חיבור משלה, כי ההגדרות נשמרות עם prefix של ה-`systemId`.
- **`ActionContext.loadSystemRemote(systemName, defaultDir)`**: טוען את החיבור מהגדרות המערכת. אם חסרים פרטים, רושם בלוג שגיאה שמפנה ל-⚙ של המערכת ומחזיר `null`, והפעולה מחזירה `FAILED`.
- **`RemoteInSystemSettingsNote(systemName)`**: הערה במסך ההגדרות של פעולה: "פרטי החיבור נמצאים בהגדרות המערכת".

### פעולה גנרית: בדיקת עדכון אחרון (`core/remote/LastUpdateCheck`)
פעולת `Logged` מוכנה (`id = check_last_update`, אפשר לתזמן) שבודקת ב-`fileInfo` מתי כל קובץ ברשימה עודכן בשרת. היא מציגה את הזמן לפי שעון ישראל ואת הזמן שחלף, ומסמנת כ**ישן** קובץ שלא עודכן יותר מהסף. הסטטוס: הכל עדכני = הצלחה, יש קובץ ישן = הצלחה חלקית, חסר / שגיאה / כשל בחיבור = נכשל.

```kotlin
private val checkLastUpdate = LastUpdateCheck(
    description = R.string.my_check_description,  // התיאור של הפעולה במערכת הזו
    systemName = NAME,                            // const – ראו למטה
    defaultRemoteDir = DEFAULT_REMOTE_DIR,
    files = listOf("data.json"),
    defaultStaleHours = 1,
)
override val actions = listOf(checkLastUpdate.action)
```

- הכותרת, ההגדרות (סף בשעות 1–8760 במפתח `stale_hours`, תזמון והתראה בכשל) והלוג משותפים לכל המערכות. רק התיאור, רשימת הקבצים וברירת המחדל של הסף שונים.
- אם יש קובץ אחד בלבד, לא מוצגת שורת "הקובץ הישן ביותר".
- את שם המערכת מעבירים כ-`const val NAME`, ולא כ-`name`. הפעולה נוצרת בזמן האתחול של ה-`object` של המערכת, ו-`const` נקבע כבר בזמן הקומפילציה.
- בשימוש היום ב-[paisresults](systems/paisresults.md) וב-[flightstatus](systems/flightstatus.md).

### זמנים
`core/model/TimeFormat.kt`:
- `formatDateTime(instant, zone)`: `dd/MM/yyyy HH:mm:ss` באזור זמן נתון. לשעון ישראל יש קבוע: `ISRAEL_ZONE` (`Asia/Jerusalem`, כולל שעון קיץ).
- `formatElapsed(context, from, now)`: "לפני 2 ימ׳ 3 שע׳". מוצגות עד שתי היחידות הגדולות. מתחת לדקה: "לפני פחות מדקה". זמן עתידי (הפרש שעונים בין שרת למכשיר): "… בעתיד".
- `formatDuration(context, duration)`: משך ריצה. מתחת לדקה "3.2 שנ׳", ומעל דקה "2 דק׳ 5 שנ׳".

### תזמון
- `ScheduleSection` (טופס) ו-`ActionScheduler` (WorkManager periodic, מינימום 15 דקות, רק כשיש רשת).
- `ScheduledActionWorker` מריץ את הפעולה דרך אותו `ActionRunner`, ושולח התראה אם ההרצה לא הצליחה במלואה (ניתן לכבות).
- אנדרואיד עשוי לדחות הרצות (Doze), כך שזה לא תזמון מדויק לדקה.

### התראות (`core/run/Notifications`)
יש שני ערוצים:
- **"כשלים בפעולות מתוזמנות"** (`runProblem`): נשלחת אוטומטית מ-`ScheduledActionWorker`. פעולה לא קוראת לזה בעצמה.
- **"עדכונים מהמערכות"** (`systemUpdate(context, tag, title, text, details)`): פעולה שולחת התראה על משהו **חדש** שאינו תקלה, למשל מיילים חדשים. התראה עם אותו `tag` מחליפה את הקודמת במקום להצטבר. בדרך כלל שולחים אותה רק כש-`trigger == SCHEDULED`, כי בהרצה ידנית המשתמש כבר רואה את הלוג.

## איך מוסיפים מערכת חדשה

1. יוצרים תיקייה `systems/<name>/`.
2. יוצרים `object MySystem : ManagedSystem` עם `id` יציב, שם, קטגוריה ואייקון.
3. אם יש הגדרות שמשותפות לכמה פעולות (למשל חיבור לשרת), מגדירים `settingsScreen` ברמת המערכת. לאתר עם חיבור FTP/SFTP בלבד: `RemoteSystemSettingsScreen`.
4. מוסיפים את הפעולות (ראו למטה). קודם בודקים אם יש פעולה גנרית מוכנה, למשל `LastUpdateCheck`. כל הטקסטים נכנסים ל-`values/strings.xml` ול-`values-iw/strings.xml` עם prefix של המערכת (למשל `pais_`, `flight_`, `mail_`). לטקסטים משותפים ב-core יש prefix לפי הנושא (`remote_`, `last_update_`, `log_`, `run_duration_`). לפני שמוסיפים מחרוזת, מוודאים שהשם לא קיים כבר, כי שם כפול מכשיל את ה-build.
5. מוסיפים `MySystem` ל-`SystemRegistry.systems`.
6. מתעדים ב-`docs/systems/<name>.md` ומוסיפים שורה לטבלה ב-`README.md`.

## איך מוסיפים פעולה

```kotlin
object MyAction {
    const val ID = "my_action"          // יציב! משמש להגדרות ולהיסטוריה

    val action = SystemAction.Logged(
        id = ID,
        title = R.string.my_action_title,
        icon = Icons.Default.Build,
        description = R.string.my_action_description,
        settingsScreen = { settings, onBack -> MySettingsScreen(settings, onBack) }, // אופציונלי
        schedulable = true,                                                         // אופציונלי
        run = {
            log.info(getString(R.string.my_action_started))
            // ... לוגיקה; קבצים זמניים ב-workDir
            RunStatus.SUCCESS
        },
    )
}
```

מסכי ההגדרות נבנים מהרכיבים הקיימים. חיבור לשרת הוא בדרך כלל הגדרה **של המערכת** (`ManagedSystem.settingsScreen`). אם זה כל מה שיש במסך, משתמשים ב-`RemoteSystemSettingsScreen(NAME, settings, DEFAULT_REMOTE_DIR, onBack)`. אם יש גם שדות נוספים, בונים את המסך בעצמכם:

```kotlin
val remote = rememberRemoteFormState(systemSettings, defaultDir = "/path")
SettingsScaffold(title, loaded = remote.loaded, onBack,
    onSave = { remote.validationError() ?: run { remote.save(); null } }) {
    RemoteSettingsSection(remote)
}
```

תזמון הוא תמיד הגדרה **של הפעולה** (`SystemAction.Logged.settingsScreen`):

```kotlin
val schedule = rememberScheduleFormState(settings)
SettingsScaffold(title, loaded = schedule.loaded, onBack,
    onSave = { schedule.saveAndApply(context); null }) {
    ScheduleSection(schedule)
}
```

ובזמן ריצה: `loadSystemRemote(NAME, defaultDir) ?: return RunStatus.FAILED` (או `RemoteSettings.load(settings, defaultDir)` לחיבור ברמת הפעולה), ואז `RemoteUploader.create(config)`.

## כללים

- **`id` של מערכת ושל פעולה לא משתנים** אחרי שיש נתונים שמורים, כי ההגדרות וההיסטוריה מקושרות אליהם.
- `core/` לא מכיר אף מערכת (חוץ מ-`ScheduledActionWorker` שמחפש ב-`SystemRegistry`).
- כל טקסט שמוצג למשתמש נמצא ב-string resources, באנגלית ובעברית (ראו "שפות").

## הגדרות האפליקציה

מסך ⚙ במסך הבית (`AppSettingsScreen`). ההגדרות מחולקות לקטגוריות לפי הסדר הזה:

1. **אבטחה:** נעילת אפליקציה, נעילה מחדש (ראו "נעילת אפליקציה").
2. **תצוגה ושפה:** מצב תצוגה, שפה.

כל הגדרה פותחת דיאלוג בחירה (`ChoiceDialog`), והבחירה מוחלת מיד. הגדרה חדשה נכנסת לקטגוריה המתאימה (`CategoryHeader`) עם אייקון, ובדיאלוג משלה.

- **מצב תצוגה** (`ThemeMode`: לפי המכשיר / בהיר / כהה). נשמר ב-`AppPreferences` (SharedPreferences, כי צריך לקרוא אותו באופן סינכרוני ב-`Application.onCreate`) ומוחל עם `AppCompatDelegate.setDefaultNightMode`.
- **שפה** (`AppLanguage`: אוטומטי / English / עברית). משתמש ב-per-app language של אנדרואיד דרך `AppCompatDelegate.setApplicationLocales`. ב-Android 13 ומעלה המערכת שומרת את הבחירה (והיא מופיעה גם בהגדרות המכשיר, דרך `res/xml/locales_config.xml`). בגרסאות קודמות AppCompat שומר אותה (`autoStoreLocales` במניפסט).

שתי ההגדרות יוצרות מחדש את ה-Activity, ולכן כל המסכים מתעדכנים.

## שפות

- `res/values/strings.xml`: אנגלית (ברירת מחדל, גם לכל שפה אחרת של המכשיר).
- `res/values-iw/strings.xml`: עברית (`iw` הוא קוד המשאבים של עברית באנדרואיד).
- כיוון התצוגה (RTL/LTR) נקבע לפי השפה, בלי לכפות כיוון.
- **בקוד Compose:** `stringResource(R.string.x)`.
- **בפעולה:** `getString(R.string.x, args)` של `ActionContext`, שהוא Context בשפת האפליקציה.
- **מחוץ ל-UI** (Worker, התראות): `context.localized()`. ב-Android 12 ומטה ה-Application context לא מתעדכן לבד לשפה שנבחרה.
- **בקוד בלי Context** (uploaders, ולידציה): זורקים `LocalizedException(R.string.x, args)` או מחזירים string res, ומתרגמים ב-`describe(context)`.
- **הלוג נשמר כטקסט**, בשפה שהייתה פעילה בזמן ההרצה. הרצות ישנות לא מתורגמות אחרי החלפת שפה.

## נעילת אפליקציה

כניסה לאפליקציה רק אחרי אימות מול נעילת המכשיר.

- **`DeviceAuth`** (`core/security`): עטיפה של `BiometricPrompt` עם `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`, כלומר טביעת אצבע או זיהוי פנים, עם PIN / pattern / סיסמה כגיבוי. נבחר WEAK ולא STRONG, כי ברוב המכשירים זיהוי פנים מסווג כ-weak, והצירוף STRONG + DEVICE_CREDENTIAL לא נתמך לפני Android 11. האפליקציה מקבלת רק הצלחה או כשל, בלי שום מידע ביומטרי.
  - **`requireConfirmation`**: בכניסה לאפליקציה (`LockScreen`) הערך הוא `false`. לכן אחרי זיהוי פנים תקין נכנסים מיד, בלי ללחוץ "אישור" (`setConfirmationRequired(false)`). זה רמז למערכת (Android 10 ומעלה), ויצרן רשאי להתעלם ממנו. בשינוי הגדרות הנעילה האישור **נשאר**, כי זו פעולה שמחלישה אבטחה ולכן נדרשת כוונה מפורשת.
- **`AppLock`**: מחזיק את מצב הנעילה (`locked: StateFlow`) ונרשם ל-`ProcessLifecycleOwner`:
  - **בהפעלה:** נעולה, אם הנעילה פעילה.
  - **חזרה מהרקע:** ננעלת אם עבר הזמן שהוגדר (`RelockTimeout`: מיד / דקה / 5 דקות / רק בסגירה).
  - **יציאה עם "חזרה":** ננעלת (`MainActivity.onDestroy` כש-`isFinishing`).
  - **בזמן אימות** (`authenticating`): מעבר לרקע לא נספר. ב-Android 10 ומטה מסך ה-PIN הוא Activity נפרד, ובלי זה האפליקציה הייתה ננעלת שוב מיד אחרי האימות.
- **`LockScreen`**: מסך שמכסה את האפליקציה ומציג את דיאלוג האימות אוטומטית. ה-NavHost נשאר מתחת לנעילה, כך שאחרי ביטול הנעילה חוזרים לאותו מסך. "חזרה" במסך הנעול יוצאת מהאפליקציה.
- **מכשיר בלי נעילת מסך:** מוצגת הודעה עם כפתור להגדרות האבטחה של המכשיר ואפשרות "המשך ללא נעילה". אין מול מה לאמת, ולכן אין טעם לחסום.
- **שינוי הגדרות הנעילה** (כיבוי או זמן נעילה מחדש) דורש אימות.
- **`FLAG_SECURE`** על ה-Activity: אין צילומי מסך, ואין תצוגה מקדימה במסך האפליקציות האחרונות.
- **פעולות מתוזמנות** (WorkManager) ממשיכות לרוץ גם כשהאפליקציה נעולה. הנעילה מגינה רק על הממשק.

## ניווט ויציאה

- **"חזרה" במסך הבית:** הלחיצה הראשונה מציגה toast "לחץ שוב על חזרה כדי לצאת". לחיצה נוספת תוך שנייה יוצאת מהאפליקציה (`DoubleBackToExit` ב-`HomeScreens.kt`). ה-toast מוסתר בקוד אחרי שנייה, כי המשך הקצר של toast באנדרואיד הוא כ-2 שניות.
- יציאה מהאפליקציה נועלת אותה לפתיחה הבאה, אם הנעילה פעילה.
- **בשאר המסכים** "חזרה" מנווט למסך הקודם. במסך הנעילה "חזרה" יוצא מהאפליקציה.

## אייקון ומיתוג

- **אייקון האפליקציה:** adaptive icon וקטורי (`mipmap-anydpi/ic_launcher*.xml`). אין קבצי bitmap, כי minSdk 26 תמיד משתמש באייקון ה-adaptive.
  - `drawable/ic_launcher_background.xml`: גרדיאנט אינדיגו-סגול (`#4F46E5` → `#7C3AED`).
  - `drawable/ic_launcher_foreground.xml`: גריד 2×2 של אריחים (המערכות). האריח הענבר (`#FBBF24`) עם ▶ מייצג הרצת פעולה. התוכן נמצא בתוך האזור הבטוח (66dp).
  - `drawable/ic_launcher_monochrome.xml`: גרסה בצבע אחד ל-Themed Icons (Android 13+).
- **`drawable/ic_notification.xml`**: אייקון בצבע אחד לשורת ההתראות. אסור להשתמש שם באייקון הצבעוני.
- **`AppLogo` / `LogoTitle`**: הלוגו בתוך ה-UI (כותרות מסך הבית וההגדרות, מסך הנעילה). `painterResource` לא תומך ב-adaptive icon, ולכן הרקע והחזית מורכבים ידנית, והחזית מוגדלת פי 1.6.
