# adapters/gui/compose (Android, not started)

The Jetpack Compose UI for the Android app. It will:

* draw `TrafficScreenLayout` from `presentation/screens/traffic` for the window's size class
  (`WindowSizeClass.of(widthDp, heightDp)`), exactly like `adapters/gui/javafx` does on desktop;
* map `shared/design-tokens` (`DesignTokens.palette(...)`, `Rgba.argb()`) to its colour scheme, so both themes
  match the desktop app;
* talk to the core only through the ports in `contexts/relay` and `contexts/inspection`.

This folder is not in `settings.gradle.kts` yet: it needs the Android Gradle plugin, which the desktop build
does not use.
