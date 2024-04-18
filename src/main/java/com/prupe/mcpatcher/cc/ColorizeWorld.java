package com.prupe.mcpatcher.cc;

import static com.prupe.mcpatcher.cc.Colorizer.loadFloatColor;
import static com.prupe.mcpatcher.cc.Colorizer.loadIntColor;
import static com.prupe.mcpatcher.cc.Colorizer.setColor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;

import com.prupe.mcpatcher.mal.biome.BiomeAPI;
import com.prupe.mcpatcher.mal.biome.ColorMap;
import com.prupe.mcpatcher.mal.biome.ColorMapBase;
import com.prupe.mcpatcher.mal.biome.ColorUtils;
import com.prupe.mcpatcher.mal.biome.IColorMap;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;

import jss.notfine.config.MCPatcherForgeConfig;

public class ColorizeWorld {

    private static final int fogBlendRadius = MCPatcherForgeConfig.instance().fogBlendRadius;

    private static final ResourceLocation UNDERWATERCOLOR = TexturePackAPI
        .newMCPatcherResourceLocation("colormap/underwater.png");
    private static final ResourceLocation UNDERLAVACOLOR = TexturePackAPI
        .newMCPatcherResourceLocation("colormap/underlava.png");
    private static final ResourceLocation FOGCOLOR0 = TexturePackAPI.newMCPatcherResourceLocation("colormap/fog0.png");
    private static final ResourceLocation SKYCOLOR0 = TexturePackAPI.newMCPatcherResourceLocation("colormap/sky0.png");

    private static final String TEXT_KEY = "text.";
    private static final String TEXT_CODE_KEY = TEXT_KEY + "code.";

    private static final int CLOUDS_DEFAULT = -1;
    private static final int CLOUDS_NONE = 0;
    private static final int CLOUDS_FAST = 1;
    private static final int CLOUDS_FANCY = 2;
    private static int cloudType = CLOUDS_DEFAULT;

    private static Entity fogCamera;

    private static final Map<Integer, Integer> textColorMap = new HashMap<>(); // text.*
    private static final int[] textCodeColors = new int[32]; // text.code.*
    private static final boolean[] textCodeColorSet = new boolean[32];
    private static int signTextColor; // text.sign

    static IColorMap underwaterColor;
    private static IColorMap underlavaColor;
    private static IColorMap fogColorMap;
    private static IColorMap skyColorMap;

    public static float[] netherFogColor;
    public static float[] endFogColor;
    public static int endSkyColor;

    static {
        try {
            reset();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    static void reset() {
        underwaterColor = null;
        underlavaColor = null;
        fogColorMap = null;
        skyColorMap = null;

        netherFogColor = new float[] { 0.2f, 0.03f, 0.03f };
        endFogColor = new float[] { 0.075f, 0.075f, 0.094f };
        endSkyColor = 0x181818;

        cloudType = CLOUDS_DEFAULT;

        textColorMap.clear();
        Arrays.fill(textCodeColorSet, false);
        signTextColor = 0;
    }

    static void reloadFogColors(PropertiesFile properties) {
        underwaterColor = wrapFogMap(ColorMap.loadFixedColorMap(Colorizer.useFogColors, UNDERWATERCOLOR));
        underlavaColor = wrapFogMap(ColorMap.loadFixedColorMap(Colorizer.useFogColors, UNDERLAVACOLOR));
        fogColorMap = wrapFogMap(ColorMap.loadFixedColorMap(Colorizer.useFogColors, FOGCOLOR0));
        skyColorMap = wrapFogMap(ColorMap.loadFixedColorMap(Colorizer.useFogColors, SKYCOLOR0));

        loadFloatColor("fog.nether", netherFogColor);
        loadFloatColor("fog.end", endFogColor);
        endSkyColor = loadIntColor("sky.end", endSkyColor);
    }

    static IColorMap wrapFogMap(IColorMap map) {
        if (map == null) {
            return null;
        } else {
            if (fogBlendRadius > 0) {
                map = new ColorMapBase.Blended(map, fogBlendRadius);
            }
            map = new ColorMapBase.Cached(map);
            map = new ColorMapBase.Smoothed(map, 3000.0f);
            map = new ColorMapBase.Outer(map);
            return map;
        }
    }

    static void reloadCloudType(PropertiesFile properties) {
        String value = properties.getString("clouds", "")
            .toLowerCase();
        switch (value) {
            case "fast" -> cloudType = CLOUDS_FAST;
            case "fancy" -> cloudType = CLOUDS_FANCY;
            case "none" -> cloudType = CLOUDS_NONE;
        }
    }

    static void reloadTextColors(PropertiesFile properties) {
        for (int i = 0; i < textCodeColors.length; i++) {
            textCodeColorSet[i] = loadIntColor(TEXT_CODE_KEY + i, textCodeColors, i);
            if (textCodeColorSet[i] && i + 16 < textCodeColors.length) {
                textCodeColors[i + 16] = (textCodeColors[i] & 0xfcfcfc) >> 2;
                textCodeColorSet[i + 16] = true;
            }
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!key.startsWith(TEXT_KEY) || key.startsWith(TEXT_CODE_KEY)) {
                continue;
            }
            key = key.substring(TEXT_KEY.length())
                .trim();
            try {
                int newColor;
                int oldColor;
                if (key.equals("xpbar")) {
                    oldColor = 0x80ff20;
                } else if (key.equals("boss")) {
                    oldColor = 0xff00ff;
                } else {
                    oldColor = Integer.parseInt(key, 16);
                }
                newColor = Integer.parseInt(value, 16);
                textColorMap.put(oldColor, newColor);
            } catch (NumberFormatException e) {}
        }
        signTextColor = loadIntColor("text.sign", 0);
    }

    public static void setupForFog(Entity entity) {
        fogCamera = entity;
    }

    private static boolean computeFogColor(IBlockAccess blockAccess, IColorMap colorMap) {
        if (colorMap == null || fogCamera == null) {
            return false;
        } else {
            int i = (int) fogCamera.posX;
            int j = (int) fogCamera.posY;
            int k = (int) fogCamera.posZ;
            Colorizer.setColorF(colorMap.getColorMultiplierF(blockAccess, i, j, k));
            return true;
        }
    }

    public static boolean computeFogColor(WorldProvider worldProvider, float f) {
        return worldProvider.dimensionId == 0 && computeFogColor(worldProvider.worldObj, fogColorMap);
    }

    public static boolean computeSkyColor(World world, float f) {
        if (world.provider.dimensionId == 0 && computeFogColor(world, skyColorMap)) {
            computeLightningFlash(world, f);
            return true;
        } else {
            return false;
        }
    }

    public static boolean computeUnderwaterColor() {
        return computeFogColor(BiomeAPI.getWorld(), underwaterColor);
    }

    public static boolean computeUnderlavaColor() {
        return computeFogColor(BiomeAPI.getWorld(), underlavaColor);
    }

    private static void computeLightningFlash(World world, float f) {
        if (world.lastLightningBolt > 0) {
            f = 0.45f * ColorUtils.clamp(world.lastLightningBolt - f);
            setColor[0] = setColor[0] * (1.0f - f) + 0.8f * f;
            setColor[1] = setColor[1] * (1.0f - f) + 0.8f * f;
            setColor[2] = setColor[2] * (1.0f - f) + 0.8f * f;
        }
    }

    public static boolean drawFancyClouds(boolean fancyGraphics) {
        return switch (cloudType) {
            case CLOUDS_NONE, CLOUDS_FAST -> false;
            case CLOUDS_FANCY -> true;
            default -> fancyGraphics;
        };
    }

    public static int colorizeText(int defaultColor) {
        int high = defaultColor & 0xff000000;
        defaultColor &= 0xffffff;
        Integer newColor = textColorMap.get(defaultColor);
        if (newColor == null) {
            return high | defaultColor;
        } else {
            return high | newColor;
        }
    }

    public static int colorizeText(int defaultColor, int index) {
        if (index < 0 || index >= textCodeColors.length || !textCodeColorSet[index]) {
            return defaultColor;
        } else {
            return (defaultColor & 0xff000000) | textCodeColors[index];
        }
    }

    public static int colorizeSignText() {
        return signTextColor;
    }
}
