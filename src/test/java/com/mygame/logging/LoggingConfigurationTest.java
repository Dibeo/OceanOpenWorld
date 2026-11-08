package com.mygame.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies command-line options that control console logging. */
final class LoggingConfigurationTest {
    /** Verifies that the short logging option enables console output. */
    @Test
    void enablesConsoleLoggingForShortFlag() {
        assertTrue(LoggingConfiguration.consoleEnabled(new String[] { "-l" }));
    }

    /** Verifies that the long logging option enables console output. */
    @Test
    void enablesConsoleLoggingForLongFlag() {
        assertTrue(LoggingConfiguration.consoleEnabled(new String[] { "--logs" }));
    }

    /** Verifies that console output remains disabled when no supported option is present. */
    @Test
    void leavesConsoleLoggingDisabledWithoutFlag() {
        assertFalse(LoggingConfiguration.consoleEnabled(new String[0]));
        assertFalse(LoggingConfiguration.consoleEnabled(new String[] { "--other" }));
    }
}
