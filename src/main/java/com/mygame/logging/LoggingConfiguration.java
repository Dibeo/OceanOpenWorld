package com.mygame.logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Initializes file logging and optional console logging before the engine starts. */
public final class LoggingConfiguration {
    private static final String CONSOLE_LEVEL_PROPERTY = "openworld.console.level";
    private static final String JUL_LOG_MANAGER = "org.apache.logging.log4j.jul.LogManager";

    private LoggingConfiguration() {}

    /**
     * Configures Log4j2 for DEBUG-and-higher file output and enables console output on request.
     *
     * @param args application command-line arguments
     */
    public static void initialize(String[] args) {
        boolean consoleEnabled = consoleEnabled(args);
        System.setProperty(CONSOLE_LEVEL_PROPERTY, consoleEnabled ? "DEBUG" : "OFF");
        System.setProperty("java.util.logging.manager", JUL_LOG_MANAGER);

        try {
            Files.createDirectories(Path.of("logs"));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create the logs directory", e);
        }

        Logger logger = LogManager.getLogger(LoggingConfiguration.class);
        logger.info("Logging initialized; file=logs/openworld.log, console={}", consoleEnabled);
    }

    static boolean consoleEnabled(String[] args) {
        return args != null && Arrays.stream(args).anyMatch(arg -> "-l".equals(arg) || "--logs".equals(arg));
    }
}
