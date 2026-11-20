# Third-party notices

The app's own code is MIT-licensed (see [LICENSE](LICENSE)). These parts keep their own licences:

| What | Project | Licence |
|---|---|---|
| `app/src/main/res/font/amiri_*.ttf` | [Amiri](https://github.com/aliftype/amiri) | SIL Open Font License 1.1 ([licenses/Amiri-OFL.txt](licenses/Amiri-OFL.txt); also inside the APK) |
| `app/src/main/res/font/plex_arabic_*.ttf` | [IBM Plex Sans Arabic](https://github.com/IBM/plex) | SIL Open Font License 1.1 ([licenses/IBMPlexSansArabic-OFL.txt](licenses/IBMPlexSansArabic-OFL.txt); also inside the APK) |
| Brand logos on the About screen (`ui/about/BrandPaths.kt`) | [Simple Icons](https://simpleicons.org) | CC0 1.0 ([licenses/SimpleIcons-CC0.md](licenses/SimpleIcons-CC0.md)); brand logos belong to their owners |
| Prayer-time calculation | [Adhan](https://github.com/batoulapps/adhan-java) (`com.batoulapps.adhan:adhan2`) | MIT |
| UI icons | [Material Icons](https://fonts.google.com/icons) (via `material-icons-extended`) | Apache 2.0 |
| Libraries | AndroidX, Jetpack Compose, Kotlin, kotlinx.coroutines, kotlinx.serialization, WorkManager | Apache 2.0 |

Reminders (`quotes.json`, `salah.json`) and the app icon come from the [Time Is a Sword extension](https://github.com/mahm0udnasr/time-is-a-sword) (MIT, same author).

Prayer times for a typed city are provided at run time by the [Aladhan API](https://aladhan.com/prayer-times-api).
