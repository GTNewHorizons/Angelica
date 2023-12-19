package com.prupe.mcpatcher;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.minecraft.launchwrapper.Launch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Config {

    private static Config instance;

    private static final File configFile = new File(Launch.minecraftHome, "config" + File.separator + "mcpatcher.json");

    private Map<String, String> logging;

    Map<String, Map<String, String>> profile;

    public static Config getInstance() {
        if (instance == null) {
            loadConfig();
        }
        return instance;
    }

    static Level getLogLevel(String category) {
        Level level = Level.INFO;
        String value = getInstance().logging.get(category);
        if (value != null) {
            try {
                level = Level.parse(
                    value.trim()
                        .toUpperCase());
            } catch (Throwable e) {}
        }
        setLogLevel(category, level);
        return level;
    }

    static void setLogLevel(String category, Level level) {
        getInstance().logging.put(
            category,
            level.toString()
                .toUpperCase());
    }

    static void loadConfig() {
        Gson gson = new Gson();
        try (Reader reader = Files.newBufferedReader(configFile.toPath())) {
            instance = gson.fromJson(reader, Config.class);
        } catch (IOException e) {
            Logger.getLogger("mcpatcher")
                .warning("Failed to read mcpatcher json config file, using defaults");
            instance = new Config();
            if (instance.logging == null) {
                instance.logging = new LinkedHashMap<>();
            }
            if (instance.profile == null) {
                instance.profile = new LinkedHashMap<>();
            }
            saveConfig();
        }

    }

    static void saveConfig() {
        Gson gsonBuilder = new GsonBuilder().setPrettyPrinting()
            .create();
        try (FileWriter writer = new FileWriter(configFile)) {
            gsonBuilder.toJson(instance, writer);
        } catch (IOException e) {
            Logger.getLogger("mcpatcher")
                .warning("Failed to save mc patcher config, settings not saved");
        }
    }

    /**
     * Gets a value from the config
     *
     * @param tag          property name
     * @param defaultValue default value if not found in profile
     * @return String value
     */
    public static String getString(String mod, String tag, Object defaultValue) {
        Map<String, String> modConfig = getInstance().getModConfig(mod);
        String value = modConfig.get(tag);
        if (value == null) {
            modConfig.put(tag, defaultValue.toString());
            saveConfig();
            return defaultValue.toString();
        } else {
            return value;
        }
    }

    /**
     * Gets a value from the config.
     *
     * @param mod          name of mod
     * @param tag          property name
     * @param defaultValue default value if not found in profile
     * @return int value or 0
     */
    public static int getInt(String mod, String tag, int defaultValue) {
        int value;
        try {
            value = Integer.parseInt(getString(mod, tag, defaultValue));
        } catch (NumberFormatException e) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * Gets a value from the config
     *
     * @param mod          name of mod
     * @param tag          property name
     * @param defaultValue default value if not found in profile
     * @return boolean value
     */
    public static boolean getBoolean(String mod, String tag, boolean defaultValue) {
        String value = getString(mod, tag, defaultValue).toLowerCase();
        if (value.equals("false")) {
            return false;
        } else if (value.equals("true")) {
            return true;
        } else {
            return defaultValue;
        }
    }

    /**
     * Sets a value in mcpatcher.json.
     *
     * @param mod   name of mod
     * @param tag   property name
     * @param value property value (must support toString())
     */
    public static void set(String mod, String tag, Object value) {
        if (value == null) {
            remove(mod, tag);
            return;
        }
        getInstance().getModConfig(mod)
            .put(tag, value.toString());
        saveConfig();
    }

    /**
     * Remove a value from mcpatcher.json.
     *
     * @param mod name of mod
     * @param tag property name
     */
    private static void remove(String mod, String tag) {
        getInstance().getModConfig(mod)
            .remove(tag);
    }

    private Map<String, String> getModConfig(String mod) {
        return this.profile.computeIfAbsent(mod, k -> new LinkedHashMap<>());
    }

}
