package com.gtnewhorizons.angelica;

import static java.util.logging.Level.ALL;
import static java.util.logging.Level.CONFIG;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.util.Calendar;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public abstract class ALog {

    private static class SMCFormatter extends Formatter {

        private int tzOffset;

        private SMCFormatter() {
            tzOffset = Calendar.getInstance().getTimeZone().getRawOffset();
        }

        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder();
            // int time = (int)((record.getMillis() + tzOffset) % (24*60*60*1000));
            sb.append("[")
                    // .append(time/36000000).append(time/3600000%10)//.append(":")
                    // .append(time/600000%6).append(time/60000%10)//.append(":")
                    // .append(time/10000%6).append(time/10000%10).append(".")
                    // .append(time/100%10).append(time/10%10).append(time%10).append(" ")
                    .append(record.getLoggerName()).append('/').append(record.getLevel()).append("] ")
                    .append(record.getMessage()).append("\n");
            return sb.toString();
        }
    }

    public static final Logger LOGGER;
    
    static {
        LOGGER = Logger.getLogger("angelica");
        LOGGER.setUseParentHandlers(false);
        Formatter formatter = new SMCFormatter();
        Handler handler = new ConsoleHandler();
        handler.setFormatter(formatter);
        LOGGER.addHandler(handler);
        try {
            handler = new FileHandler("angelica-%u.log", 8 * 1048576, 1, false);
            handler.setFormatter(formatter);
            LOGGER.addHandler(handler);
        } catch (IOException e) {
            e.printStackTrace();
        }
        LOGGER.setLevel(ALL);
    }

    public static void log(Level level, String format, Object... args) {
        if (LOGGER.isLoggable(level)) LOGGER.log(level, String.format(format, args));
    }

    public static void severe(String format, Object... args) {
        if (LOGGER.isLoggable(SEVERE)) LOGGER.log(SEVERE, String.format(format, args));
    }

    public static void warning(String format, Object... args) {
        if (LOGGER.isLoggable(WARNING)) LOGGER.log(WARNING, String.format(format, args));
    }

    public static void info(String format, Object... args) {
        if (LOGGER.isLoggable(INFO)) LOGGER.log(INFO, String.format(format, args));
    }

    public static void config(String format, Object... args) {
        if (LOGGER.isLoggable(CONFIG)) LOGGER.log(CONFIG, String.format(format, args));
    }

    public static void fine(String format, Object... args) {
        if (LOGGER.isLoggable(FINE)) LOGGER.log(FINE, String.format(format, args));
    }

    public static void finer(String format, Object... args) {
        if (LOGGER.isLoggable(FINER)) LOGGER.log(FINER, String.format(format, args));
    }

    public static void finest(String format, Object... args) {
        if (LOGGER.isLoggable(FINEST)) LOGGER.log(FINEST, String.format(format, args));
    }
}
