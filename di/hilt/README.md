# di/hilt (Android, not started)

Dependency injection for the Android app. It will do what `di/dagger` does on desktop, with Android's choices:
`adapters/connection/tcp` (unchanged, `java.net` works on Android), `adapters/storage/room` and
`adapters/gui/compose`. Core classes carry no injection annotations, so Hilt creates them with the same plain
constructors `di/dagger` uses.

Not in `settings.gradle.kts` yet: it needs the Android Gradle plugin.
