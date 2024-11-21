package com.prupe.mcpatcher;

import jss.notfine.config.MCPatcherForgeConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.*;

public class MCLogger {

    private static final org.apache.logging.log4j.Logger MAIN_LOGGER = org.apache.logging.log4j.LogManager.getLogger("MCPatcherForge");

    private static final Map<String, MCLogger> allLoggers = new HashMap<>();

    public static final Level ERROR = new ErrorLevel();

    private static final long FLOOD_INTERVAL = 1000L;
    private static final long FLOOD_REPORT_INTERVAL = 5000L;
    private static final int FLOOD_LEVEL = Level.INFO.intValue();

    private final String logPrefix;
    private final Logger logger;

    private boolean flooding;
    private long lastFloodReport;
    private int floodCount;
    private long lastMessage = System.currentTimeMillis();

    public static MCLogger getLogger(Category category) {
        return getLogger(category, category.name);
    }

    public static synchronized MCLogger getLogger(Category category, String logPrefix) {
        MCLogger logger = allLoggers.get(category.name);
        if (logger == null) {
            logger = new MCLogger(category, logPrefix);
            allLoggers.put(category.name, logger);
        }
        return logger;
    }

    private MCLogger(Category category, String logPrefix) {
        this.logPrefix = logPrefix;
        logger = Logger.getLogger(category.name);
        logger.setLevel(switch (category) {
            case CUSTOM_COLORS -> MCPatcherForgeConfig.CustomColors.logging.level;
            case CUSTOM_ITEM_TEXTURES -> MCPatcherForgeConfig.CustomItemTextures.logging.level;
            case CONNECTED_TEXTURES -> MCPatcherForgeConfig.ConnectedTextures.logging.level;
            case EXTENDED_HD -> MCPatcherForgeConfig.ExtendedHD.logging.level;
            case RANDOM_MOBS -> MCPatcherForgeConfig.RandomMobs.logging.level;
            case BETTER_SKIES -> MCPatcherForgeConfig.BetterSkies.logging.level;
            default -> Level.INFO;
        });

        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {

            private final Formatter formatter = new Formatter() {

                @Override
                public String format(LogRecord record) {
                    Level level = record.getLevel();
                    if (level == Level.CONFIG) {
                        return record.getMessage();
                    } else {
                        String message = record.getMessage();
                        StringBuilder prefix = new StringBuilder();
                        while (message.startsWith("\n")) {
                            prefix.append("\n");
                            message = message.substring(1);
                        }
                        return prefix + "[" + MCLogger.this.logPrefix + "/" + level.toString() + "]: " + message;
                    }
                }
            };

            @Override
            public void publish(LogRecord record) {
                MAIN_LOGGER.info(formatter.format(record));
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        });
    }

    private boolean checkFlood() {
        long now = System.currentTimeMillis();
        boolean showFloodMessage = false;
        if (now - lastMessage > FLOOD_INTERVAL) {
            if (flooding) {
                reportFlooding(now);
                flooding = false;
            } else {
                floodCount = 0;
            }
        } else if (flooding && now - lastFloodReport > FLOOD_REPORT_INTERVAL) {
            reportFlooding(now);
            showFloodMessage = true;
        }
        lastMessage = now;
        floodCount++;
        if (flooding) {
            return showFloodMessage;
        } else {
            return true;
        }
    }

    private void reportFlooding(long now) {
        if (floodCount > 0) {
            logger.log(
                Level.WARNING,
                String
                    .format("%d flood messages dropped in the last %ds", floodCount, (now - lastFloodReport) / 1000L));
        }
        floodCount = 0;
        lastFloodReport = now;
    }

    public boolean isLoggable(Level level) {
        return logger.isLoggable(level);
    }

    public void log(Level level, String format, Object... params) {
        if (isLoggable(level)) {
            if (level.intValue() >= FLOOD_LEVEL && !checkFlood()) {
                return;
            }
            logger.log(level, String.format(format, params));
        }
    }

    public void severe(String format, Object... params) {
        log(Level.SEVERE, format, params);
    }

    public void error(String format, Object... params) {
        log(ERROR, format, params);
    }

    public void warning(String format, Object... params) {
        log(Level.WARNING, format, params);
    }

    public void info(String format, Object... params) {
        log(Level.INFO, format, params);
    }

    public void config(String format, Object... params) {
        log(Level.CONFIG, format, params);
    }

    public void fine(String format, Object... params) {
        log(Level.FINE, format, params);
    }

    public void finer(String format, Object... params) {
        log(Level.FINER, format, params);
    }

    public void finest(String format, Object... params) {
        log(Level.FINEST, format, params);
    }

    private static class ErrorLevel extends Level {

        protected ErrorLevel() {
            super("ERROR", (Level.WARNING.intValue() + Level.SEVERE.intValue()) / 2);
        }
    }

    public enum Category {

        CUSTOM_COLORS(MCPatcherUtils.CUSTOM_COLORS),
        CUSTOM_ITEM_TEXTURES(MCPatcherUtils.CUSTOM_ITEM_TEXTURES),
        CONNECTED_TEXTURES(MCPatcherUtils.CONNECTED_TEXTURES),
        EXTENDED_HD(MCPatcherUtils.EXTENDED_HD),
        RANDOM_MOBS(MCPatcherUtils.RANDOM_MOBS),
        BETTER_SKIES(MCPatcherUtils.BETTER_SKIES),
        TEXTURE_PACK("Texture Pack"),
        TILESHEET("Tilesheet"),
        BETTER_GLASS(MCPatcherUtils.BETTER_GLASS),

        ;

        public final String name;

        Category(String name) {
            this.name = name;
        }
    }
}
