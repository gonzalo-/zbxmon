# Contributing

Thanks for helping! A few simple rules:

- Code, comments, commit messages and documentation are written in **English**.
- User-facing text lives in `app/src/main/res/values/strings.xml` (English, default) and
  `app/src/main/res/values-es/strings.xml` (Spanish). Never hard-code UI text in Kotlin.
  Every key must exist in both files. To add a language, create `values-<code>/strings.xml`
  and add it to `AppLanguage` in `data/Storage.kt`.
- Keep the app free of Google Play Services and trackers.
- Before opening a pull request, make sure `./gradlew assembleDebug` succeeds and
  `python3 scripts/check-strings.py` reports that translations are in sync (CI runs both).

## Project layout
```
app/src/main/java/dev/zbxmon/
  data/     models, storage, Keystore encryption, TLS pinning, Zabbix JSON-RPC client
  notify/   notifications, WorkManager worker, foreground service, boot receiver
  widget/   home screen widget
  ui/       Compose screens, theme, view model
```
