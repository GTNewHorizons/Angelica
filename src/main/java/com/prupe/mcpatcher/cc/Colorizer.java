package com.prupe.mcpatcher.cc;

import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.MCPatcherUtils;
import com.prupe.mcpatcher.mal.biome.ColorMap;
import com.prupe.mcpatcher.mal.biome.ColorUtils;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;
import com.prupe.mcpatcher.mal.resource.TexturePackChangeHandler;

import mist475.mcpatcherforge.config.MCPatcherForgeConfig;

public class Colorizer {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.CUSTOM_COLORS);

    static final ResourceLocation COLOR_PROPERTIES = TexturePackAPI.newMCPatcherResourceLocation("color.properties");
    private static PropertiesFile properties;

    static final boolean usePotionColors = MCPatcherForgeConfig.instance().ccPotion;
    static final boolean useParticleColors = MCPatcherForgeConfig.instance().ccParticle;
    static final boolean useFogColors = MCPatcherForgeConfig.instance().ccFog;
    static final boolean useCloudType = MCPatcherForgeConfig.instance().ccClouds;
    static final boolean useMapColors = MCPatcherForgeConfig.instance().ccMap;
    static final boolean useDyeColors = MCPatcherForgeConfig.instance().ccDye;
    static final boolean useTextColors = MCPatcherForgeConfig.instance().ccText;
    static final boolean useXPOrbColors = MCPatcherForgeConfig.instance().ccXPOrb;
    static final boolean useEggColors = MCPatcherForgeConfig.instance().ccEgg;

    public static final float[] setColor = new float[3];

    static {
        try {
            reset();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        TexturePackChangeHandler.register(new TexturePackChangeHandler(MCPatcherUtils.CUSTOM_COLORS, 2) {

            @Override
            public void beforeChange() {
                reset();
            }

            @Override
            public void afterChange() {
                reloadColorProperties();
                ColorMap.reloadColorMapSettings(properties);
                if (useParticleColors) {
                    ColorizeEntity.reloadParticleColors(properties);
                }
                ColorizeBlock.reloadAll(properties);
                if (useFogColors) {
                    ColorizeWorld.reloadFogColors(properties);
                }
                if (usePotionColors) {
                    ColorizeItem.reloadPotionColors(properties);
                }
                if (useCloudType) {
                    ColorizeWorld.reloadCloudType(properties);
                }
                if (useMapColors) {
                    ColorizeItem.reloadMapColors(properties);
                }
                if (useDyeColors) {
                    ColorizeEntity.reloadDyeColors(properties);
                }
                if (useTextColors) {
                    ColorizeWorld.reloadTextColors(properties);
                }
                if (useXPOrbColors) {
                    ColorizeEntity.reloadXPOrbColors(properties);
                }
            }
        });
    }

    public static void setColorF(int color) {
        ColorUtils.intToFloat3(color, setColor);
    }

    static void setColorF(float[] color) {
        setColor[0] = color[0];
        setColor[1] = color[1];
        setColor[2] = color[2];
    }

    public static void init() {}

    private static void reset() {
        properties = new PropertiesFile(logger, COLOR_PROPERTIES);

        ColorMap.reset();
        ColorizeBlock.reset();
        Lightmap.reset();
        ColorizeItem.reset();
        ColorizeWorld.reset();
        ColorizeEntity.reset();
    }

    private static void reloadColorProperties() {
        properties = PropertiesFile.getNonNull(logger, COLOR_PROPERTIES);
        logger.finer("reloading %s", properties);
    }

    static String getStringKey(String[] keys, int index) {
        if (keys != null && index >= 0 && index < keys.length && keys[index] != null) {
            return keys[index];
        } else {
            return "" + index;
        }
    }

    static void loadIntColor(String key, Potion potion) {
        potion.liquidColor = loadIntColor(key, potion.getLiquidColor());
    }

    static boolean loadIntColor(String key, int[] color, int index) {
        logger.config("%s=%06x", key, color[index]);
        String value = properties.getString(key, "");
        if (!value.isEmpty()) {
            try {
                color[index] = Integer.parseInt(value, 16);
                return true;
            } catch (NumberFormatException e) {}
        }
        return false;
    }

    static int loadIntColor(String key, int color) {
        logger.config("%s=%06x", key, color);
        return properties.getHex(key, color);
    }

    static void loadFloatColor(String key, float[] color) {
        int intColor = ColorUtils.float3ToInt(color);
        ColorUtils.intToFloat3(loadIntColor(key, intColor), color);
    }

    static Integer loadIntegerColor(String key) {
        int[] tmp = new int[1];
        if (loadIntColor(key, tmp, 0)) {
            return tmp[0];
        } else {
            return null;
        }
    }

    static float[] loadFloatColor(String key) {
        Integer color = loadIntegerColor(key);
        if (color == null) {
            return null;
        } else {
            float[] rgb = new float[3];
            ColorUtils.intToFloat3(color, rgb);
            return rgb;
        }
    }
}
