package com.mygame.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LoggingConfigurationTest {
    @Test
    void enablesConsoleLoggingForShortFlag() {
        assertTrue(LoggingConfiguration.consoleEnabled(new String[] { "-l" }));
    }

    @Test
    void enablesConsoleLoggingForLongFlag() {
        assertTrue(LoggingConfiguration.consoleEnabled(new String[] { "--logs" }));
    }

    @Test
    void leavesConsoleLoggingDisabledWithoutFlag() {
        assertFalse(LoggingConfiguration.consoleEnabled(new String[0]));
        assertFalse(LoggingConfiguration.consoleEnabled(new String[] { "--other" }));
    }
}
