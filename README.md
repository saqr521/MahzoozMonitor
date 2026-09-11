# MahzoozMonitor-Pro FINAL v4

نسخة مراقبة شاشة تعتمد على MediaProjection.

## ما تم إصلاحه
- تشغيل محلل الشبكة `findTiles()` في كل لقطة بدل تركه غير مستخدم.
- ربط العلامات 🟩/🟨 بأقرب عنصر فعلي في الشبكة قبل استخدام القص الاحتياطي.
- حفظ علاقات 🟩 ← 🟨 في SQLite بدل الاعتماد على لقطة واحدة.
- حفظ انتقالات الرصد في SQLite حتى لا تضيع عند إغلاق الخدمة.
- إصلاح Workflow البناء ليبني المشروع مباشرة من جذر Gradle.
- تحسين حساب الثقة والسجل.

## مهم
التطبيق يصف العلاقات والانتقالات التي رصدها من الشاشة. لا يمكنه تحويل لعبة عشوائية إلى توقع مضمون.

## البناء
افتح مجلد `MahzoozMonitor-Pro` كجذر المشروع، أو شغّل Workflow `Build APK - Pro` من GitHub Actions.


## v5 — coordinates, relation rates, and strip/storage reporting
- Reports detected green/yellow marker coordinates as `x,y,width,height`.
- Reports observed green→yellow relation rates from the local history database.
- Reports relation hit counts (`hits/total`) so a percentage is never presented without its sample size.
- Reports detected item center coordinates when the visual matcher identifies the item.
- Keeps the existing screen-capture workflow and does not claim a random-game outcome is guaranteed.


## Algorithm Trace (read-only)
The app records the observable path `GAME_SCREEN -> MONITOR -> OVERLAY`: detected markers, detected item labels, coordinates, relations, and monitor outputs. It also supports logging an intended action as `MONITOR -> GAME (NOT SENT)` for auditing. It does **not** inspect another app's private memory or network traffic and does not inject taps/commands into the game.
