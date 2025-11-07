package com.gtnewhorizons.angelica.compat.iris;

import net.coderbot.iris.parsing.BiomeCategories;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Detects and categorizes biomes from popular modded biome mods using cached class checks.
 * Currently supports: BiomesOPlenty (BOP), Realistic World Gen (RWG)
 */
public class ModdedBiomeDetector {
    private static boolean initialized = false;

    // BiomesOPlenty (BOP) cache - dimension-specific base classes
    // BOP 1.7.10 hierarchy: BOPBiome -> BOPEndBiome/BOPNetherBiome/BOPOverworldBiome -> specific biomes
    private static Class<?> bopEndBiomeClass = null;
    private static Class<?> bopNetherBiomeClass = null;
    private static Class<?> bopOceanBiomeClass = null;

    // Realistic World Gen (RWG) cache - base biome classes that extend BiomeGenBase
    // LinkedHashMap maintains insertion order for deterministic behavior
    private static final Map<Class<?>, BiomeCategories> rwgClassMap = new LinkedHashMap<>();

    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        initializeBOP();
        initializeRWG();
    }

    private static void initializeBOP() {
        try {
            bopEndBiomeClass = Class.forName("biomesoplenty.common.biome.BOPEndBiome");
            bopNetherBiomeClass = Class.forName("biomesoplenty.common.biome.BOPNetherBiome");
            bopOceanBiomeClass = Class.forName("biomesoplenty.common.biome.BOPOceanBiome");
        } catch (ClassNotFoundException ignored) {
            // BOP not installed
        }
    }

    private static void initializeRWG() {
        // RWG adds actual BiomeGenBase subclasses in rwg.biomes.base package
        tryAddRWGClass("rwg.biomes.base.BaseBiomeOcean", BiomeCategories.OCEAN);
        tryAddRWGClass("rwg.biomes.base.BaseBiomeRiver", BiomeCategories.RIVER);
        tryAddRWGClass("rwg.biomes.base.BaseBiomePlains", BiomeCategories.PLAINS);
        tryAddRWGClass("rwg.biomes.base.BaseBiomeColdPlains", BiomeCategories.PLAINS);
        tryAddRWGClass("rwg.biomes.base.BaseBiomeHotPlains", BiomeCategories.PLAINS);
        tryAddRWGClass("rwg.biomes.base.BaseBiomeHotDesert", BiomeCategories.DESERT);
        tryAddRWGClass("rwg.biomes.base.BaseBiomeSnowDesert", BiomeCategories.DESERT); // Cold desert, but still desert
        tryAddRWGClass("rwg.biomes.base.BaseBiomeJungle", BiomeCategories.JUNGLE);
        tryAddRWGClass("rwg.biomes.base.BaseBiomeTropicalIsland", BiomeCategories.JUNGLE); // Tropical islands are jungle-like
        tryAddRWGClass("rwg.biomes.base.BaseBiomeRedwood", BiomeCategories.FOREST); // Redwood/Sequoia forests
        tryAddRWGClass("rwg.biomes.base.BaseBiomeTemperateForest", BiomeCategories.FOREST);
        tryAddRWGClass("rwg.biomes.base.BaseBiomeColdForest", BiomeCategories.TAIGA); // Cold forests are taiga-like
        tryAddRWGClass("rwg.biomes.base.BaseBiomeHotForest", BiomeCategories.FOREST);
        tryAddRWGClass("rwg.biomes.base.BaseBiomeSnowForest", BiomeCategories.TAIGA); // Snow forests are taiga
    }

    private static void tryAddRWGClass(String className, BiomeCategories category) {
        try {
            final Class<?> clazz = Class.forName(className);
            rwgClassMap.put(clazz, category);
        } catch (ClassNotFoundException ignored) {
            // Class not available, RWG not installed or different version
        }
    }

    /**
     * Attempts to detect the biome category for modded biomes.
     * Returns null if no modded biome detection applies.
     */
    public static BiomeCategories detectModdedBiome(BiomeGenBase biome) {
        if (!initialized) {
            initialize();
        }

        // BiomesOPlenty detection - dimension-specific classes give us clear categories
        if (bopEndBiomeClass != null && bopEndBiomeClass.isInstance(biome)) {
            return BiomeCategories.THE_END;
        }
        if (bopNetherBiomeClass != null && bopNetherBiomeClass.isInstance(biome)) {
            return BiomeCategories.NETHER;
        }
        if (bopOceanBiomeClass != null && bopOceanBiomeClass.isInstance(biome)) {
            return BiomeCategories.OCEAN;
        }
        // For other BOP overworld biomes (e.g., Alps, Arctic, BambooForest),
        // fall through to name/property-based detection

        // Realistic World Gen detection
        for (Map.Entry<Class<?>, BiomeCategories> entry : rwgClassMap.entrySet()) {
            if (entry.getKey().isInstance(biome)) {
                return entry.getValue();
            }
        }

        return null;
    }
}
