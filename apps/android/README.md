# apps/android (not started)

The Android app: its `Activity` (the Android equivalent of `Main.java`), the manifest and packaging. It will
use `di/hilt` to get its object graph, the way `apps/windows` uses `di/dagger`.

Not in `settings.gradle.kts` yet: it needs the Android Gradle plugin.
