package com.gtnewhorizons.angelica.config;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigMigrator {
    public static Logger LOGGER = LogManager.getLogger("Angelica");

    /**
     * Tries to migrate the equivalent config file from Rubidium to the Angelica name if possible.
     */
    public static Path handleConfigMigration(String fileName) {
        final Path configDir = Minecraft.getMinecraft().mcDataDir.toPath().resolve("config");
        final Path mainPath = configDir.resolve(fileName);
        try {
            if(Files.notExists(mainPath)) {
                final String legacyName = fileName.replace("angelica", "rubidium");
                final Path legacyPath = configDir.resolve(legacyName);
                if(Files.exists(legacyPath)) {
                    Files.move(legacyPath, mainPath);
                    LOGGER.warn("Migrated {} config file to {}", legacyName, fileName);
                }
            }
        } catch(IOException | RuntimeException e) {
            LOGGER.error("Exception encountered while attempting config migration", e);
        }

        return mainPath;
    }
}
