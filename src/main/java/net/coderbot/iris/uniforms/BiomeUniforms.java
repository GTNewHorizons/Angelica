package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.compat.iris.BiomeCategoryCache;
import com.gtnewhorizons.angelica.compat.iris.ModdedBiomeDetector;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.parsing.BiomeCategories;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.BiomeGenBeach;
import net.minecraft.world.biome.BiomeGenDesert;
import net.minecraft.world.biome.BiomeGenEnd;
import net.minecraft.world.biome.BiomeGenForest;
import net.minecraft.world.biome.BiomeGenHell;
import net.minecraft.world.biome.BiomeGenHills;
import net.minecraft.world.biome.BiomeGenJungle;
import net.minecraft.world.biome.BiomeGenMesa;
import net.minecraft.world.biome.BiomeGenMushroomIsland;
import net.minecraft.world.biome.BiomeGenMutated;
import net.minecraft.world.biome.BiomeGenOcean;
import net.minecraft.world.biome.BiomeGenPlains;
import net.minecraft.world.biome.BiomeGenRiver;
import net.minecraft.world.biome.BiomeGenSavanna;
import net.minecraft.world.biome.BiomeGenSnow;
import net.minecraft.world.biome.BiomeGenStoneBeach;
import net.minecraft.world.biome.BiomeGenSwamp;
import net.minecraft.world.biome.BiomeGenTaiga;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_TICK;

public class BiomeUniforms {

    private static final Logger LOGGER = LogManager.getLogger("BiomeUniforms");
    private static final Minecraft client = Minecraft.getMinecraft();

    // Cache to avoid multiple biome lookups per tick
    private static BiomeGenBase cachedBiome = null;
    private static long cachedWorldTime = -1;
    private static int cachedPlayerX = Integer.MIN_VALUE;
    private static int cachedPlayerZ = Integer.MIN_VALUE;

    /**
     * Adds biome-related uniforms that change based on the player's current biome.
     */
    public static void addBiomeUniforms(UniformHolder uniforms) {
        uniforms.uniform1i(PER_TICK, "biome", BiomeUniforms::getBiomeId)
                .uniform1i(PER_TICK, "biome_category", BiomeUniforms::getBiomeCategory)
                .uniform1i(PER_TICK, "biome_precipitation", BiomeUniforms::getBiomePrecipitation)
                .uniform1f(PER_TICK, "rainfall", BiomeUniforms::getBiomeRainfall)
                .uniform1f(PER_TICK, "temperature", BiomeUniforms::getBiomeTemperature);
    }

    /**
     * Gets the current biome, with caching to avoid repeated lookups within the same tick.
     * Returns null if player or world is not available.
     */
    private static BiomeGenBase getCachedBiome() {
        if (client.thePlayer == null || client.theWorld == null) {
            return null;
        }

        final long worldTime = client.theWorld.getTotalWorldTime();
        final int playerX = MathHelper.floor_double(client.thePlayer.posX);
        final int playerZ = MathHelper.floor_double(client.thePlayer.posZ);

        // Invalidate cache if time or position changed
        if (cachedBiome == null || cachedWorldTime != worldTime || cachedPlayerX != playerX || cachedPlayerZ != playerZ) {
            cachedBiome = client.theWorld.getBiomeGenForCoords(playerX, playerZ);
            cachedWorldTime = worldTime;
            cachedPlayerX = playerX;
            cachedPlayerZ = playerZ;
        }

        return cachedBiome;
    }

    public static int getBiomePrecipitation() {
        final BiomeGenBase biome = getCachedBiome();
        if (biome == null) {
            return 0;
        }

        if (!biome.enableRain && !biome.enableSnow) {
            return 0;
        }

        final float temp = biome.getFloatTemperature(
                MathHelper.floor_double(client.thePlayer.posX),
                MathHelper.floor_double(client.thePlayer.posY),
                MathHelper.floor_double(client.thePlayer.posZ));

        return temp > 0.15F ? 1 : 2;
    }

    public static float getBiomeRainfall() {
        final BiomeGenBase biome = getCachedBiome();
        return biome != null ? biome.rainfall : 0.0F;
    }

    public static float getBiomeTemperature() {
        final BiomeGenBase biome = getCachedBiome();
        return biome != null ? biome.temperature : 0.0F;
    }

    public static int getBiomeId() {
        final BiomeGenBase biome = getCachedBiome();
        return biome != null ? biome.biomeID : 0;
    }

    public static int getBiomeCategory() {
        final BiomeGenBase biome = getCachedBiome();
        if (biome == null) {
            return BiomeCategories.NONE.ordinal();
        }

        // Check cache first
        if (biome instanceof BiomeCategoryCache cache) {
            final int cached = cache.iris$getCachedCategory();
            if (cached != -1) {
                return cached;
            }

            // Not cached yet, determine and cache it
            final int category = determineBiomeCategory(biome);
            cache.iris$setCachedCategory(category);
            final BiomeCategories[] categories = BiomeCategories.values();
            final String categoryName = (category >= 0 && category < categories.length)
                ? categories[category].name()
                : "INVALID(" + category + ")";
            LOGGER.debug("Cached biome category for '{}': {}", biome.biomeName, categoryName);

            return category;
        }

        // Fallback if mixin didn't apply (shouldn't happen)
        return determineBiomeCategory(biome);
    }

    private static int determineBiomeCategory(BiomeGenBase biome) {
        // Tier 1: Hardcoded vanilla biome IDs
        BiomeGenBase lookupBiome = biome;
        if (biome instanceof BiomeGenMutated mutated && mutated.baseBiome != null) {
            lookupBiome = mutated.baseBiome;
        }

        BiomeCategories category = getVanillaBiomeCategory(lookupBiome.biomeID);
        if (category != null) {
            return category.ordinal();
        }

        // Tier 2: Class-based detection for vanilla biome types
        category = detectVanillaBiomeByClass(biome);
        if (category != null) {
            return category.ordinal();
        }

        // Tier 3: Modded biome detection (BiomesOPlenty, Realistic World Gen)
        category = detectModdedBiome(biome);
        if (category != null) {
            return category.ordinal();
        }

        // Tier 4: Name pattern matching
        category = detectBiomeByName(biome);
        if (category != null) {
            return category.ordinal();
        }

        // Tier 5: Temperature/rainfall heuristics
        category = detectBiomeByProperties(biome);
        if (category != null) {
            return category.ordinal();
        }

        // Default: NONE
        return BiomeCategories.NONE.ordinal();
    }

    private static BiomeCategories getVanillaBiomeCategory(int biomeID) {
        return switch (biomeID) {
            // OCEAN
            case 0, 10, 24 -> BiomeCategories.OCEAN; // ocean, frozenOcean, deepOcean

            // PLAINS
            case 1 -> BiomeCategories.PLAINS; // plains

            // DESERT
            case 2, 17 -> BiomeCategories.DESERT; // desert, desertHills

            // EXTREME_HILLS (Vanilla mountains)
            case 3, 20, 34 -> BiomeCategories.EXTREME_HILLS; // extremeHills, extremeHillsEdge, extremeHillsPlus

            // FOREST
            case 4, 18, 27, 28, 29 ->
                    BiomeCategories.FOREST; // forest, forestHills, birchForest, birchForestHills, roofedForest

            // TAIGA
            case 5, 19, 30, 31, 32, 33 ->
                    BiomeCategories.TAIGA; // taiga, taigaHills, coldTaiga, coldTaigaHills, megaTaiga, megaTaigaHills

            // SWAMP
            case 6 -> BiomeCategories.SWAMP; // swampland

            // RIVER
            case 7, 11 -> BiomeCategories.RIVER; // river, frozenRiver

            // NETHER
            case 8 -> BiomeCategories.NETHER; // hell

            // THE_END
            case 9 -> BiomeCategories.THE_END; // sky

            // ICY
            case 12, 13 -> BiomeCategories.ICY; // icePlains, iceMountains

            // MUSHROOM
            case 14, 15 -> BiomeCategories.MUSHROOM; // mushroomIsland, mushroomIslandShore

            // BEACH
            case 16, 25, 26 -> BiomeCategories.BEACH; // beach, stoneBeach, coldBeach

            // JUNGLE
            case 21, 22, 23 -> BiomeCategories.JUNGLE; // jungle, jungleHills, jungleEdge

            // SAVANNA
            case 35, 36 -> BiomeCategories.SAVANNA; // savanna, savannaPlateau

            // MESA
            case 37, 38, 39 -> BiomeCategories.MESA; // mesa, mesaPlateau_F, mesaPlateau

            default -> null; // Not a vanilla biome or unknown
        };
    }

    private static final Map<Class<? extends BiomeGenBase>, BiomeCategories> VANILLA_CLASS_MAP = createVanillaClassMap();

    private static Map<Class<? extends BiomeGenBase>, BiomeCategories> createVanillaClassMap() {
        final Map<Class<? extends BiomeGenBase>, BiomeCategories> map = new HashMap<>();
        // Most specific first (subclasses before superclasses)
        map.put(BiomeGenStoneBeach.class, BiomeCategories.BEACH);
        map.put(BiomeGenBeach.class, BiomeCategories.BEACH);
        map.put(BiomeGenMushroomIsland.class, BiomeCategories.MUSHROOM);
        map.put(BiomeGenOcean.class, BiomeCategories.OCEAN);
        map.put(BiomeGenPlains.class, BiomeCategories.PLAINS);
        map.put(BiomeGenDesert.class, BiomeCategories.DESERT);
        map.put(BiomeGenHills.class, BiomeCategories.EXTREME_HILLS);
        map.put(BiomeGenForest.class, BiomeCategories.FOREST);
        map.put(BiomeGenTaiga.class, BiomeCategories.TAIGA);
        map.put(BiomeGenSwamp.class, BiomeCategories.SWAMP);
        map.put(BiomeGenRiver.class, BiomeCategories.RIVER);
        map.put(BiomeGenHell.class, BiomeCategories.NETHER);
        map.put(BiomeGenEnd.class, BiomeCategories.THE_END);
        map.put(BiomeGenSnow.class, BiomeCategories.ICY);
        map.put(BiomeGenJungle.class, BiomeCategories.JUNGLE);
        map.put(BiomeGenSavanna.class, BiomeCategories.SAVANNA);
        map.put(BiomeGenMesa.class, BiomeCategories.MESA);
        return map;
    }

    private static BiomeCategories detectVanillaBiomeByClass(BiomeGenBase biome) {
        // Direct class lookup (O(1) for exact matches)
        final BiomeCategories direct = VANILLA_CLASS_MAP.get(biome.getClass());
        if (direct != null) {
            return direct;
        }

        // Fallback: instanceof checks for subclasses (modded biomes extending vanilla)
        for (Map.Entry<Class<? extends BiomeGenBase>, BiomeCategories> entry : VANILLA_CLASS_MAP.entrySet()) {
            if (entry.getKey().isInstance(biome)) {
                return entry.getValue();
            }
        }

        return null;
    }

    private static BiomeCategories detectModdedBiome(BiomeGenBase biome) {
        return ModdedBiomeDetector.detectModdedBiome(biome);
    }

    private static BiomeCategories detectBiomeByName(BiomeGenBase biome) {
        if (biome.biomeName == null) return null;

        final String name = biome.biomeName.toLowerCase();

        // Check for keywords in biome names
        // Priority order matters - check more specific terms first

        if (name.contains("nether")) return BiomeCategories.NETHER;
        if (name.contains("end")) return BiomeCategories.THE_END;

        if (name.contains("ocean") || name.contains("sea")) return BiomeCategories.OCEAN;
        if (name.contains("river") || name.contains("stream")) return BiomeCategories.RIVER;
        if (name.contains("beach") || name.contains("shore") || name.contains("coast")) return BiomeCategories.BEACH;

        if (name.contains("mushroom")) return BiomeCategories.MUSHROOM;
        if (name.contains("swamp") || name.contains("marsh") || name.contains("bog")) return BiomeCategories.SWAMP;

        if (name.contains("jungle")) return BiomeCategories.JUNGLE;
        if (name.contains("savanna") || name.contains("savannah")) return BiomeCategories.SAVANNA;
        if (name.contains("mesa") || name.contains("badlands")) return BiomeCategories.MESA;
        if (name.contains("desert")) return BiomeCategories.DESERT;

        // Mountain detection - for modded biomes
        if (name.contains("mountain") || name.contains("peak") || name.contains("alpine") ||
            name.contains("cliff") || name.contains("crag")) return BiomeCategories.MOUNTAIN;

        // Ice/Snow detection
        if (name.contains("ice") || name.contains("frozen") || name.contains("snow") ||
            name.contains("arctic") || name.contains("tundra") || name.contains("glacier")) return BiomeCategories.ICY;

        if (name.contains("taiga") || name.contains("boreal") || name.contains("conifer")) return BiomeCategories.TAIGA;

        if (name.contains("forest") || name.contains("wood") || name.contains("grove") ||
            name.contains("thicket")) return BiomeCategories.FOREST;

        if (name.contains("plain") || name.contains("field") || name.contains("meadow") ||
            name.contains("grassland") || name.contains("prairie")) return BiomeCategories.PLAINS;

        return null;
    }

    private static BiomeCategories detectBiomeByProperties(BiomeGenBase biome) {
        final float temp = biome.temperature;
        final float rain = biome.rainfall;

        // Very cold biomes with snow
        if (temp <= 0.0F && biome.enableSnow) {
            return BiomeCategories.ICY;
        }

        // Hot, dry biomes
        if (temp >= 2.0F && !biome.enableRain) {
            return BiomeCategories.DESERT;
        }

        // Warm, dry biomes (savanna-like)
        if (temp >= 1.0F && rain <= 0.1F && !biome.enableRain) {
            return BiomeCategories.SAVANNA;
        }

        // Wet biomes with high rainfall
        if (rain >= 0.85F && temp >= 0.5F && temp <= 1.0F) {
            return BiomeCategories.SWAMP;
        }

        // Cold forested biomes
        if (temp >= 0.0F && temp <= 0.4F && rain >= 0.4F) {
            return BiomeCategories.TAIGA;
        }

        // Temperate forested biomes
        if (temp >= 0.4F && temp <= 0.9F && rain >= 0.5F) {
            return BiomeCategories.FOREST;
        }

        // Default to plains for temperate, moderate biomes
        if (temp >= 0.4F && temp <= 1.0F && rain >= 0.3F && rain <= 0.6F) {
            return BiomeCategories.PLAINS;
        }

        return null;
    }
}
