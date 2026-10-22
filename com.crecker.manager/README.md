# Crecker Manager

אפליקציית אנדרואיד לניהול מערכות (אתרים, שרתים וכו'). לכל מערכת יש מסך משלו עם הפעולות שרלוונטיות רק לה.

## מערכות קיימות

| מערכת | פעולות | תיעוד |
|---|---|---|
| paisresults.co.il | ביצוע cron data retrieval | [docs/systems/paisresults.md](docs/systems/paisresults.md) |

## מבנה

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) – איך האפליקציה בנויה ואיך מוסיפים מערכת/פעולה חדשה.
- [CLAUDE.md](CLAUDE.md) – קונבנציות הפרויקט.

## הרצה

- פתיחה ב-Android Studio והרצה רגילה, או משורת הפקודה:
  ```
  gradlew :app:assembleDebug        # בנייה
  gradlew :app:testDebugUnitTest    # בדיקות
  ```
- minSdk 26, Kotlin + Jetpack Compose (Material 3).
- הממשק בעברית ומוצג תמיד RTL.
