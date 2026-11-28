package io.github.mralireza82.wirelens.di.dagger;

import dagger.BindsInstance;
import dagger.Component;
import io.github.mralireza82.wirelens.adapter.gui.javafx.AppDependencies;
import io.github.mralireza82.wirelens.relay.application.proxy.ProxyService;

import javax.inject.Singleton;

/** The object graph of a desktop app (Windows, Linux). Built once by the platform's Main. */
@Singleton
@Component(modules = DesktopModule.class)
public interface DesktopComponent {
    /** Everything the JavaFX UI needs. */
    AppDependencies ui();

    /** The relay, for start-up extras (demo traffic) and a clean stop on exit. */
    ProxyService proxy();

    static DesktopComponent create(LaunchOptions options) {
        return DaggerDesktopComponent.factory().create(options);
    }

    @Component.Factory
    interface Factory {
        DesktopComponent create(@BindsInstance LaunchOptions options);
    }
}
