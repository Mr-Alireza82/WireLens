/**
 * Dependency injection for desktop platforms with Dagger: picks the adapters (TCP, SQLite, files, JavaFX) and
 * builds the application services. The only module, besides the apps, allowed to see concrete adapters.
 */
package io.github.mralireza82.wirelens.di.dagger;
