package net.coderbot.iris.uniforms;

import net.minecraft.world.biome.BiomeGenBase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VanillaBiomeList {
    public static class BiomeEntry {
        public final BiomeGenBase biome;
        public final String name;

        public BiomeEntry(BiomeGenBase biome, String name) {
            this.biome = biome;
            this.name = name;
        }
    }

    private static final List<BiomeEntry> VANILLA_BIOMES = createVanillaBiomeList();

    private static List<BiomeEntry> createVanillaBiomeList() {
        final List<BiomeEntry> biomes = new ArrayList<>();

        // Vanilla biomes from BiomeGenBase static fields (IDs 0-39)
        biomes.add(new BiomeEntry(BiomeGenBase.ocean, "OCEAN"));
        biomes.add(new BiomeEntry(BiomeGenBase.plains, "PLAINS"));
        biomes.add(new BiomeEntry(BiomeGenBase.desert, "DESERT"));
        biomes.add(new BiomeEntry(BiomeGenBase.extremeHills, "EXTREME_HILLS"));
        biomes.add(new BiomeEntry(BiomeGenBase.forest, "FOREST"));
        biomes.add(new BiomeEntry(BiomeGenBase.taiga, "TAIGA"));
        biomes.add(new BiomeEntry(BiomeGenBase.swampland, "SWAMPLAND"));
        biomes.add(new BiomeEntry(BiomeGenBase.river, "RIVER"));
        biomes.add(new BiomeEntry(BiomeGenBase.hell, "HELL"));
        biomes.add(new BiomeEntry(BiomeGenBase.sky, "SKY"));
        biomes.add(new BiomeEntry(BiomeGenBase.frozenOcean, "FROZEN_OCEAN"));
        biomes.add(new BiomeEntry(BiomeGenBase.frozenRiver, "FROZEN_RIVER"));
        biomes.add(new BiomeEntry(BiomeGenBase.icePlains, "ICE_PLAINS"));
        biomes.add(new BiomeEntry(BiomeGenBase.iceMountains, "ICE_MOUNTAINS"));
        biomes.add(new BiomeEntry(BiomeGenBase.mushroomIsland, "MUSHROOM_ISLAND"));
        biomes.add(new BiomeEntry(BiomeGenBase.mushroomIslandShore, "MUSHROOM_ISLAND_SHORE"));
        biomes.add(new BiomeEntry(BiomeGenBase.beach, "BEACH"));
        biomes.add(new BiomeEntry(BiomeGenBase.desertHills, "DESERT_HILLS"));
        biomes.add(new BiomeEntry(BiomeGenBase.forestHills, "FOREST_HILLS"));
        biomes.add(new BiomeEntry(BiomeGenBase.taigaHills, "TAIGA_HILLS"));
        biomes.add(new BiomeEntry(BiomeGenBase.extremeHillsEdge, "EXTREME_HILLS_EDGE"));
        biomes.add(new BiomeEntry(BiomeGenBase.jungle, "JUNGLE"));
        biomes.add(new BiomeEntry(BiomeGenBase.jungleHills, "JUNGLE_HILLS"));
        biomes.add(new BiomeEntry(BiomeGenBase.jungleEdge, "JUNGLE_EDGE"));
        biomes.add(new BiomeEntry(BiomeGenBase.deepOcean, "DEEP_OCEAN"));
        biomes.add(new BiomeEntry(BiomeGenBase.stoneBeach, "STONE_BEACH"));
        biomes.add(new BiomeEntry(BiomeGenBase.coldBeach, "COLD_BEACH"));
        biomes.add(new BiomeEntry(BiomeGenBase.birchForest, "BIRCH_FOREST"));
        biomes.add(new BiomeEntry(BiomeGenBase.birchForestHills, "BIRCH_FOREST_HILLS"));
        biomes.add(new BiomeEntry(BiomeGenBase.roofedForest, "ROOFED_FOREST"));
        biomes.add(new BiomeEntry(BiomeGenBase.coldTaiga, "COLD_TAIGA"));
        biomes.add(new BiomeEntry(BiomeGenBase.coldTaigaHills, "COLD_TAIGA_HILLS"));
        biomes.add(new BiomeEntry(BiomeGenBase.megaTaiga, "MEGA_TAIGA"));
        biomes.add(new BiomeEntry(BiomeGenBase.megaTaigaHills, "MEGA_TAIGA_HILLS"));
        biomes.add(new BiomeEntry(BiomeGenBase.extremeHillsPlus, "EXTREME_HILLS_PLUS"));
        biomes.add(new BiomeEntry(BiomeGenBase.savanna, "SAVANNA"));
        biomes.add(new BiomeEntry(BiomeGenBase.savannaPlateau, "SAVANNA_PLATEAU"));
        biomes.add(new BiomeEntry(BiomeGenBase.mesa, "MESA"));
        biomes.add(new BiomeEntry(BiomeGenBase.mesaPlateau_F, "MESA_PLATEAU_F"));
        biomes.add(new BiomeEntry(BiomeGenBase.mesaPlateau, "MESA_PLATEAU"));

        // Modern End biome variants (1.9+) - all map to the single End biome in 1.7.10
        // This allows shaders expecting these constants to work
        biomes.add(new BiomeEntry(BiomeGenBase.sky, "THE_END"));
        biomes.add(new BiomeEntry(BiomeGenBase.sky, "SMALL_END_ISLANDS"));
        biomes.add(new BiomeEntry(BiomeGenBase.sky, "END_MIDLANDS"));
        biomes.add(new BiomeEntry(BiomeGenBase.sky, "END_HIGHLANDS"));
        biomes.add(new BiomeEntry(BiomeGenBase.sky, "END_BARRENS"));

        return Collections.unmodifiableList(biomes);
    }

    /**
     * Returns the cached list of all vanilla biomes with their shader-friendly names.
     * The list is immutable and created once during class initialization.
     */
    public static List<BiomeEntry> getVanillaBiomes() {
        return VANILLA_BIOMES;
    }
}
