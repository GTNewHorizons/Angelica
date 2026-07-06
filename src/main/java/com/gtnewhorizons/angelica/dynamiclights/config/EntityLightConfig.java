package com.gtnewhorizons.angelica.dynamiclights.config;

import com.gtnewhorizon.gtnhlib.concurrent.cas.CasMap;
import com.gtnewhorizons.angelica.dynamiclights.IDynamicLightSource;
import cpw.mods.fml.common.registry.EntityRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityLightConfig {
    private static final Logger LOGGER = LogManager.getLogger("Angelica/DynamicLights");

    private static final Set<String> disabledClassNames = ConcurrentHashMap.newKeySet();
    private static final CasMap<Class<?>, Boolean> enabledCache = new CasMap<>();

    private static File configFile;
    private static boolean initialized = false;

    private EntityLightConfig() {}

    public static void init(@NotNull File configDir) {
        if (initialized) return;

        configFile = new File(configDir, "angelica-dynamiclights-entities.txt");
        load();
        initialized = true;
    }

    public static boolean isEntityTypeEnabled(@NotNull IDynamicLightSource source) {
        if (!initialized) return true;
        return isEntityTypeEnabled(source.getClass());
    }

    public static boolean isEntityTypeEnabled(@NotNull Class<?> entityClass) {
        if (!initialized) return true;

        Boolean cached = enabledCache.get(entityClass);
        if (cached != null) {
            return cached;
        }

        final boolean enabled = !disabledClassNames.contains(entityClass.getName());
        Boolean existing = enabledCache.putIfAbsent(entityClass, enabled);
        return existing != null ? existing : enabled;
    }

    public static void setEntityTypeEnabled(@NotNull Class<?> entityClass, boolean enabled) {
        enabledCache.put(entityClass, enabled);

        final String className = entityClass.getName();
        if (enabled) {
            disabledClassNames.remove(className);
        } else {
            disabledClassNames.add(className);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<EntityTypeEntry> getAllEntityTypes() {
        List<EntityTypeEntry> entries = new ArrayList<>();
        Set<String> seenClasses = new HashSet<>();

        Map<String, Class<? extends Entity>> stringToClass = EntityList.stringToClassMapping;

        for (Map.Entry<String, Class<? extends Entity>> entry : stringToClass.entrySet()) {
            String entityId = entry.getKey();
            Class<?> entityClass = entry.getValue();
            String className = entityClass.getName();

            if (seenClasses.contains(className)) continue;
            seenClasses.add(className);

            String modId = getModIdForEntity(entityClass, entityId);
            String displayName = getDisplayName(entityId, entityClass);
            boolean enabled = !disabledClassNames.contains(className);

            entries.add(new EntityTypeEntry(displayName, className, modId, enabled));
        }

        // Sort by mod ID, then by display name
        entries.sort((a, b) -> {
            int modCompare = a.getModId().compareToIgnoreCase(b.getModId());
            if (modCompare != 0) return modCompare;
            return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
        });

        return entries;
    }

    @SuppressWarnings("unchecked")
    private static String getModIdForEntity(Class<?> entityClass, String entityId) {
        // Check EntityRegistry for mod entity entries
        if (Entity.class.isAssignableFrom(entityClass)) {
            EntityRegistry.EntityRegistration registration = EntityRegistry.instance()
                .lookupModSpawn((Class<? extends Entity>) entityClass, false);
            if (registration != null && registration.getContainer() != null) {
                return registration.getContainer().getModId();
            }
        }

        // Check if it's a vanilla entity (no dot in entity ID)
        if (!entityId.contains(".")) {
            return "minecraft";
        }

        // Extract mod ID from entity ID (format: modid.EntityName)
        int dotIndex = entityId.indexOf('.');
        if (dotIndex > 0) {
            return entityId.substring(0, dotIndex);
        }

        return "unknown";
    }

    private static String getDisplayName(String entityId, Class<?> entityClass) {
        // Use the simple class name, removing package
        String simpleName = entityClass.getSimpleName();
        if (simpleName.isEmpty()) {
            // Anonymous class, use entity ID
            return entityId;
        }
        return simpleName;
    }

    public static void load() {
        if (configFile == null || !configFile.exists()) return;

        disabledClassNames.clear();
        enabledCache.clear();

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                // Lines are entity class names that are DISABLED
                disabledClassNames.add(line);
            }
            LOGGER.info("Loaded {} disabled entity types from config", disabledClassNames.size());
        } catch (IOException e) {
            LOGGER.warn("Failed to load entity light config: {}", e.getMessage());
        }
    }

    public static void save() {
        if (configFile == null) return;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            writer.write("# Angelica Dynamic Lights - Disabled Entity Types");
            writer.newLine();
            writer.write("# Each line is a fully qualified class name of an entity type that is DISABLED.");
            writer.newLine();
            writer.write("# Remove a line to re-enable dynamic lighting for that entity type.");
            writer.newLine();
            writer.newLine();

            List<String> sorted = new ArrayList<>(disabledClassNames);
            Collections.sort(sorted);

            for (String className : sorted) {
                writer.write(className);
                writer.newLine();
            }

            LOGGER.info("Saved {} disabled entity types to config", disabledClassNames.size());
        } catch (IOException e) {
            LOGGER.warn("Failed to save entity light config: {}", e.getMessage());
        }
    }
}
