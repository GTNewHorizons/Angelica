package com.prupe.mcpatcher.mal.biome;

import java.util.BitSet;

import net.minecraft.client.Minecraft;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.MCPatcherUtils;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;

public class BiomeAPI {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.CUSTOM_COLORS);

    public static final int WORLD_MAX_HEIGHT = 255;

    private static boolean biomesLogged;
    private static BiomeGenBase lastBiome;
    private static int lastI;
    private static int lastK;

    BiomeAPI() {}

    public static void parseBiomeList(String list, BitSet bits) {
        logBiomes();
        if (MCPatcherUtils.isNullOrEmpty(list)) {
            return;
        }
        for (String s : list.split(list.contains(",") ? "\\s*,\\s*" : "\\s+")) {
            BiomeGenBase biome = findBiomeByName(s);
            if (biome != null) {
                bits.set(biome.biomeID);
            }
        }
    }

    public static BitSet getHeightListProperty(PropertiesFile properties, String suffix) {
        int minHeight = Math.max(properties.getInt("minHeight" + suffix, 0), 0);
        int maxHeight = Math.min(properties.getInt("maxHeight" + suffix, WORLD_MAX_HEIGHT), WORLD_MAX_HEIGHT);
        String heightStr = properties.getString("heights" + suffix, "");
        if (minHeight == 0 && maxHeight == WORLD_MAX_HEIGHT && heightStr.isEmpty()) {
            return null;
        } else {
            BitSet heightBits = new BitSet(WORLD_MAX_HEIGHT + 1);
            if (heightStr.isEmpty()) {
                heightStr = minHeight + "-" + maxHeight;
            }
            for (int i : MCPatcherUtils.parseIntegerList(heightStr, 0, WORLD_MAX_HEIGHT)) {
                heightBits.set(i);
            }
            return heightBits;
        }
    }

    public static BiomeGenBase findBiomeByName(String name) {
        logBiomes();
        if (name == null) {
            return null;
        }
        name = name.replace(" ", "");
        if (name.isEmpty()) {
            return null;
        }
        for (BiomeGenBase biome : BiomeGenBase.getBiomeGenArray()) {
            if (biome == null || biome.biomeName == null) {
                continue;
            }
            if (name.equalsIgnoreCase(biome.biomeName) || name.equalsIgnoreCase(biome.biomeName.replace(" ", ""))) {
                if (biome.biomeID >= 0 && biome.biomeID < BiomeGenBase.getBiomeGenArray().length) {
                    return biome;
                }
            }
        }
        return null;
    }

    public static IBlockAccess getWorld() {
        return Minecraft.getMinecraft().theWorld;
    }

    public static int getBiomeIDAt(IBlockAccess blockAccess, int i, int j, int k) {
        BiomeGenBase biome = getBiomeGenAt(blockAccess, i, j, k);
        return biome == null ? BiomeGenBase.getBiomeGenArray().length : biome.biomeID;
    }

    public static BiomeGenBase getBiomeGenAt(IBlockAccess blockAccess, int i, int j, int k) {
        if (lastBiome == null || i != lastI || k != lastK) {
            lastI = i;
            lastK = k;
            lastBiome = blockAccess.getBiomeGenForCoords(i, k);
        }
        return lastBiome;
    }

    public static int getWaterColorMultiplier(BiomeGenBase biome) {
        return biome == null ? 0xffffff : biome.getWaterColorMultiplier();
    }

    private static void logBiomes() {
        if (!biomesLogged) {
            biomesLogged = true;
            for (int i = 0; i < BiomeGenBase.getBiomeGenArray().length; i++) {
                BiomeGenBase biome = BiomeGenBase.getBiomeGenArray()[i];
                if (biome != null) {
                    int x = (int) (255.0f * (1.0f - biome.temperature));
                    int y = (int) (255.0f * (1.0f - biome.temperature * biome.rainfall));
                    logger.config(
                        "setupBiome #%d id=%d \"%s\" %06x (%d,%d)",
                        i,
                        biome.biomeID,
                        biome.biomeName,
                        biome.waterColorMultiplier,
                        x,
                        y);
                }
            }
        }
    }
}
