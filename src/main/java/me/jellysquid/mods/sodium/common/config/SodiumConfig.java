package me.jellysquid.mods.sodium.common.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Documentation of these options: https://github.com/jellysquid3/sodium-fabric/wiki/Configuration-File
 */
public class SodiumConfig {
    private static final Logger LOGGER = LogManager.getLogger("EmbeddiumConfig");

    private final Map<String, Option> options = new HashMap<>();

    private SodiumConfig() {

    }

    private void readProperties(Properties props) {
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();

            Option option = this.options.get(key);

            if (option == null) {
                LOGGER.warn("No configuration key exists with name '{}', ignoring", key);
                continue;
            }

            boolean enabled;

            if (value.equalsIgnoreCase("true")) {
                enabled = true;
            } else if (value.equalsIgnoreCase("false")) {
                enabled = false;
            } else {
                LOGGER.warn("Invalid value '{}' encountered for configuration key '{}', ignoring", value, key);
                continue;
            }

            // TODO: Sodium Config
//            if(!enabled && FMLEnvironment.production && SYSTEM_OPTIONS.contains(key)) {
//                LOGGER.warn("Configuration key '{}' is a required option and cannot be disabled", key);
//                continue;
//            }

            option.setEnabled(enabled, true);
        }
    }

    /**
     * Loads the configuration file from the specified location. If it does not exist, a new configuration file will be
     * created. The file on disk will then be updated to include any new options.
     */
    public static SodiumConfig load(File file) {
        if (!file.exists()) {
            try {
                writeDefaultConfig(file);
            } catch (IOException e) {
                LOGGER.warn("Could not write default configuration file", e);
            }

            return new SodiumConfig();
        }

        final Properties props = new Properties();

        try (FileInputStream fin = new FileInputStream(file)){
            props.load(fin);
        } catch (IOException e) {
            throw new RuntimeException("Could not load config file", e);
        }

        final SodiumConfig config = new SodiumConfig();
        config.readProperties(props);

        return config;
    }

    private static void writeDefaultConfig(File file) throws IOException {
        final File dir = file.getParentFile();

        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Could not create parent directories");
            }
        } else if (!dir.isDirectory()) {
            throw new IOException("The parent file is not a directory");
        }

        try (Writer writer = new FileWriter(file)) {
            writer.write("# This is the configuration file for Sodium.\n");
            writer.write("#\n");
            writer.write("# You can find information on editing this file and all the available options here:\n");
            writer.write("# https://github.com/jellysquid3/sodium-fabric/wiki/Configuration-File\n");
            writer.write("#\n");
            writer.write("# By default, this file will be empty except for this notice.\n");
        }
    }

}
