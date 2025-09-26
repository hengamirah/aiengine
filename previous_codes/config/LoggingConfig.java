package com.example.config;

import java.util.logging.*;

public class LoggingConfig {

    private LoggingConfig() {
        // Utility class, prevent instantiation
    }

    public static void setupLogging() {
        Logger rootLogger = Logger.getLogger("");
        for (Handler handler : rootLogger.getHandlers()) {
            handler.setFormatter(new Formatter() {
                private final SimpleFormatter delegate = new SimpleFormatter();

                @Override
                public String format(LogRecord record) {
                    String message = record.getMessage();
                    if (message != null && message.contains("data:image") && message.length() > 200) {
                        message = message.substring(0, 200) + "... [BASE64_DATA_FILTERED]";
                        record.setMessage(message);
                    }
                    return delegate.format(record);
                }
            });
        }
    }
}