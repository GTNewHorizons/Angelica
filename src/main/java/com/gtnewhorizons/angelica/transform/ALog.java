package com.gtnewhorizons.angelica.transform;

import java.io.IOException;
import java.util.Calendar;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.util.logging.Level.ALL;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

public abstract class ALog {

    private static class SMCFormatter extends Formatter {

        int tzOffset;

        SMCFormatter() {
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
                    .append(record.getLoggerName()).append(" ").append(record.getLevel()).append("]")
                    .append(record.getMessage()).append("\n");
            return sb.toString();
        }
    }

    private static class ALogger extends Logger {

        ALogger(String name) {
            super(name, null);
            setUseParentHandlers(false);
            Formatter formatter = new SMCFormatter();
            Handler handler = new ConsoleHandler();
            handler.setFormatter(formatter);
            addHandler(handler);
            try {
                handler = new FileHandler("shadersmod-%u.log", 8 * 1048576, 1, false);
                handler.setFormatter(formatter);
                addHandler(handler);
            } catch (IOException e) {
                e.printStackTrace();
            }
            setLevel(ALL);
        }
    }

    private static class SMCLevel extends Level {

        private SMCLevel(String name, int value) {
            super(name, value);
        }
    }

    public static final String ALogName = "ANG";
    public static final Logger logger = new ALogger(ALogName);
    public static final Level ANGINFO = new SMCLevel("INF", 850);
    public static final Level ANGCONFIG = new SMCLevel("CFG", 840);
    public static final Level ANGFINE = new SMCLevel("FNE", 830);
    public static final Level ANGFINER = new SMCLevel("FNR", 820);
    public static final Level ANGFINEST = new SMCLevel("FNT", 810);

    public static void log(Level level, String format, Object... args) {
        if (logger.isLoggable(level)) logger.log(level, String.format(format, args));
    }

    public static void severe(String format, Object... args) {
        if (logger.isLoggable(SEVERE)) logger.log(SEVERE, String.format(format, args));
    }

    public static void warning(String format, Object... args) {
        if (logger.isLoggable(WARNING)) logger.log(WARNING, String.format(format, args));
    }

    public static void info(String format, Object... args) {
        if (logger.isLoggable(ANGINFO)) logger.log(ANGINFO, String.format(format, args));
    }

    public static void config(String format, Object... args) {
        if (logger.isLoggable(ANGCONFIG)) logger.log(ANGCONFIG, String.format(format, args));
    }

    public static void fine(String format, Object... args) {
        if (logger.isLoggable(ANGFINE)) logger.log(ANGFINE, String.format(format, args));
    }

    public static void finer(String format, Object... args) {
        if (logger.isLoggable(ANGFINER)) logger.log(ANGFINER, String.format(format, args));
    }

    public static void finest(String format, Object... args) {
        if (logger.isLoggable(ANGFINEST)) logger.log(ANGFINEST, String.format(format, args));
    }
}
