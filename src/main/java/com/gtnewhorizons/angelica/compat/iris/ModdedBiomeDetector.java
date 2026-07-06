package com.gtnewhorizons.angelica.compat.iris;

import com.gtnewhorizons.angelica.compat.ModStatus;
import net.coderbot.iris.parsing.BiomeCategories;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Detects and categorizes biomes from popular modded biome mods using cached class checks.
 * Currently supports: BiomesOPlenty (BOP), Realistic World Gen (RWG), lotr (b36.15)
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

    private static Class<?> lotrBiomeClass = null;

    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        initializeBOP();
        initializeRWG();
        initializeLOTR();
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

    private static void initializeLOTR() {
        if (ModStatus.isLotrLoaded) {
            try {
                lotrBiomeClass = Class.forName("lotr.common.world.biome.LOTRBiome");
            } catch (ClassNotFoundException ignored) {
                // lotr not installed
            }
        }
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
        if (biome.getBiomeClass().getName().contains("rwg.biomes")) {
            for (Map.Entry<Class<?>, BiomeCategories> entry : rwgClassMap.entrySet()) {
                if (entry.getKey().isInstance(biome)) {
                    return entry.getValue();
                }
            }
        }

        // lotr
        if (lotrBiomeClass!= null && lotrBiomeClass.isInstance(biome)) {
            return detectLOTRBiome(biome);
        }

        return null;
    }

    private static BiomeCategories detectLOTRBiome(BiomeGenBase biome) {
        return switch (biome.biomeName) {
            case "river", "farHaradJungleLake", "lake" -> BiomeCategories.RIVER;
            case "rohan", "rivendell", "rhunIsland", "rhunLandHills", "rhunLandSteppe", "rhunLand", "andrast",
                 "blackrootVale", "lamedon", "imlothMelui", "towerHills", "dorwinion", "dale", "shireMoors", "wold",
                 "anduinVale", "rhun", "lebennin", "pertorogwaith", "breeland", "island", "pinnathGelin",
                 "minhiriath", "pelennor", "adornland", "nanCurunir", "ithilienWasteland", "fangornClearing",
                 "barrowDowns", "dorEnErnilHills", "dorEnErnil", "nurn", "wilderland", "eastBight", "lindon",
                 "eregion", "enedwaith", "meneltarma", "anduinHills", "eriador", "angle", "loneLandsHills",
                 "loneLands", "pelargir", "rohanUrukHighlands", "celebrant", "gondor", "shire", "nurnen", "farHarad" ->
                BiomeCategories.PLAINS;
            case "mistyMountains", "windMountains", "farHaradVolcano", "redMountains", "haradMountains",
                 "angmarMountains", "blueMountains", "greyMountains", "whiteMountains", "mordorMountains" ->
                BiomeCategories.MOUNTAIN;
            case "shireWoodlands", "rhunIslandForest", "rhunRedForest", "pukel", "lossarnach", "gulfHaradForest",
                 "nearHaradFertileForest", "dolGuldur", "rhunForest", "tauredainClearing", "umbarForest",
                 "farHaradForest", "chetwood", "woodlandRealmHills", "mirkwoodNorth", "whiteDowns", "ithilienHills",
                 "gondorWoodlands", "rohanWoodlands", "fangornWasteland", "mirkwoodMountains", "lindonWoodlands",
                 "lothlorienEdge", "erynVorn", "oldForest", "fangorn", "dunland", "ithilien", "mirkwoodCorrupted",
                 "woodlandRealm", "trollshaws", "lothlorien", "farHaradKanuka" -> BiomeCategories.FOREST;
            case "mordor", "lastDesert", "harnedor", "easternDesolation", "morgulVale", "gorgoroth", "udun",
                 "nearHaradRedDesert", "nearHaradSemiDesert", "lostladen",
                 "nearHaradHills", "nearHarad", "nanUngol", "dagorlad", "brownLands" -> BiomeCategories.DESERT;
            case "ironHills", "rivendellHills", "windMountainsFoothills", "lamedonHills", "redMountainsFoothills",
                 "tolfalas", "dorwinionHills", "whiteMountainsFoothills", "blueMountainsFoothills",
                 "greyMountainsFoothills", "mistyMountainsFoothills", "erebor", "angmar", "eriadorDowns",
                 "ettenmoors", "emynMuil" -> BiomeCategories.EXTREME_HILLS;
            case "deadMarshes", "shireMarshes", "farHaradMangrove", "farHaradSwamp", "swanfleet", "nindalf",
                 "longMarshes", "entwashMouth", "nurnMarshes", "gladdenFields", "midgewater", "anduinMouth" ->
                BiomeCategories.SWAMP;
            case "harondor", "nearHaradOasis", "nearHaradRiverbank", "farHaradBushlandHills", "farHaradBushland",
                 "gulfHarad", "nearHaradFertile", "umbarHills", "umbar", "farHaradAridHills", "farHaradArid" ->
                BiomeCategories.SAVANNA;
            case "ocean" -> BiomeCategories.OCEAN;
            case "forodwaith", "forodwaithGlacier", "tundra", "forodwaithMountains" -> BiomeCategories.ICY;
            case "lindonCoast", "beachWhite", "farHaradCoast", "forodwaithCoast", "beachGravel", "beach" ->
                BiomeCategories.BEACH;
            case "coldfells", "wilderlandNorth", "taiga" -> BiomeCategories.TAIGA;
            case "farHaradJungle", "halfTrollForest", "farHaradJungleMountains",
                 "farHaradCloudForest", "farHaradJungleEdge" -> BiomeCategories.JUNGLE;
            case "utumno" -> BiomeCategories.NONE;
            default -> null;
        };
    }
}
