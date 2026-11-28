package io.github.mralireza82.wirelens.app.windows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Routes {@link System.Logger} output (backed by java.util.logging) to a rotating file in
 * {@code ~/.wirelens/logs} and warnings to the console. No logging library needed.
 */
final class Logging {
    private Logging() {
    }

    static void configure() {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT.%1$tL %4$-7s [%3$s] %5$s%6$s%n");
        Logger root = Logger.getLogger("");
        for (var handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        ConsoleHandler console = new ConsoleHandler();
        console.setLevel(Level.WARNING);
        root.addHandler(console);
        try {
            Path folder = Path.of(System.getProperty("user.home"), ".wirelens", "logs");
            Files.createDirectories(folder);
            FileHandler file = new FileHandler(folder.resolve("wirelens-%g.log").toString(), 2_000_000, 5, true);
            file.setFormatter(new SimpleFormatter());
            file.setLevel(Level.INFO);
            root.addHandler(file);
        } catch (IOException | SecurityException e) {
            root.log(Level.WARNING, "File logging disabled: " + e.getMessage());
        }
        root.setLevel(Level.INFO);
    }
}
