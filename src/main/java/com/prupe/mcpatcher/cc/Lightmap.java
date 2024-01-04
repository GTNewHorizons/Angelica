package com.prupe.mcpatcher.cc;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.mal.biome.ColorUtils;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;

import mist475.mcpatcherforge.config.MCPatcherForgeConfig;

public final class Lightmap {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.CUSTOM_COLORS);
    private static final String LIGHTMAP_FORMAT = "lightmap/world%d.png";
    private static final int LIGHTMAP_SIZE = 16;
    private static final int HEIGHT_WITHOUT_NIGHTVISION = 2 * LIGHTMAP_SIZE;
    private static final int HEIGHT_WITH_NIGHTVISION = 4 * LIGHTMAP_SIZE;

    private static final boolean useLightmaps = MCPatcherForgeConfig.instance().ccLightmaps;

    private static final Map<Integer, Lightmap> lightmaps = new HashMap<>();

    private final int width;
    private final boolean customNightvision;
    private final int[] origMap;
    private final boolean valid;

    private final float[] sunrgb = new float[3 * LIGHTMAP_SIZE];
    private final float[] torchrgb = new float[3 * LIGHTMAP_SIZE];
    private final float[] sunrgbnv = new float[3 * LIGHTMAP_SIZE];
    private final float[] torchrgbnv = new float[3 * LIGHTMAP_SIZE];
    private final float[] rgb = new float[3];

    static void reset() {
        lightmaps.clear();
    }

    public static boolean computeLightmap(EntityRenderer renderer, World world, int[] mapRGB, float partialTick) {
        if (world == null || !useLightmaps) {
            return false;
        }
        Lightmap lightmap = null;
        int worldType = world.provider.dimensionId;
        if (lightmaps.containsKey(worldType)) {
            lightmap = lightmaps.get(worldType);
        } else {
            ResourceLocation resource = TexturePackAPI
                .newMCPatcherResourceLocation(String.format(LIGHTMAP_FORMAT, worldType));
            BufferedImage image = TexturePackAPI.getImage(resource);
            if (image != null) {
                lightmap = new Lightmap(resource, image);
                if (!lightmap.valid) {
                    lightmap = null;
                }
            }
            lightmaps.put(worldType, lightmap);
        }
        return lightmap != null && lightmap.compute(renderer, world, mapRGB, partialTick);
    }

    private Lightmap(ResourceLocation resource, BufferedImage image) {
        width = image.getWidth();
        int height = image.getHeight();
        customNightvision = (height == HEIGHT_WITH_NIGHTVISION);
        origMap = new int[width * height];
        image.getRGB(0, 0, width, height, origMap, 0, width);
        valid = (height == HEIGHT_WITHOUT_NIGHTVISION || height == HEIGHT_WITH_NIGHTVISION);
        if (!valid) {
            logger.error(
                "%s must be exactly %d or %d pixels high",
                resource,
                HEIGHT_WITHOUT_NIGHTVISION,
                HEIGHT_WITH_NIGHTVISION);
        }
    }

    private float getNightVisionStrength(EntityRenderer renderer, float n) {

        if (Minecraft.getMinecraft().thePlayer.isPotionActive(Potion.nightVision)) {
            return renderer.getNightVisionBrightness(Minecraft.getMinecraft().thePlayer, n);
        }
        return 0.0f;
    }

    private boolean compute(EntityRenderer renderer, World world, int[] mapRGB, float partialTick) {
        float sun = ColorUtils.clamp(
            world.lastLightningBolt > 0 ? 1.0f : 7.0f / 6.0f * (world.getSunBrightness(1.0f) - 0.2f)) * (width - 1);
        float torch = ColorUtils.clamp(renderer.torchFlickerX + 0.5f) * (width - 1);
        float nightVisionStrength = getNightVisionStrength(renderer, partialTick);
        float gamma = ColorUtils.clamp(Minecraft.getMinecraft().gameSettings.gammaSetting);
        for (int i = 0; i < LIGHTMAP_SIZE; i++) {
            interpolate(origMap, i * width, sun, sunrgb, 3 * i);
            interpolate(origMap, (i + LIGHTMAP_SIZE) * width, torch, torchrgb, 3 * i);
            if (customNightvision && nightVisionStrength > 0.0f) {
                interpolate(origMap, (i + 2 * LIGHTMAP_SIZE) * width, sun, sunrgbnv, 3 * i);
                interpolate(origMap, (i + 3 * LIGHTMAP_SIZE) * width, torch, torchrgbnv, 3 * i);
            }
        }
        for (int s = 0; s < LIGHTMAP_SIZE; s++) {
            for (int t = 0; t < LIGHTMAP_SIZE; t++) {
                for (int k = 0; k < 3; k++) {
                    rgb[k] = ColorUtils.clamp(sunrgb[3 * s + k] + torchrgb[3 * t + k]);
                }
                if (nightVisionStrength > 0.0f) {
                    if (customNightvision) {
                        for (int k = 0; k < 3; k++) {
                            rgb[k] = ColorUtils.clamp(
                                (1.0f - nightVisionStrength) * rgb[k]
                                    + nightVisionStrength * (sunrgbnv[3 * s + k] + torchrgbnv[3 * t + k]));
                        }
                    } else {
                        float nightVisionMultiplier = Math.max(Math.max(rgb[0], rgb[1]), rgb[2]);
                        if (nightVisionMultiplier > 0.0f) {
                            nightVisionMultiplier = (1.0f - nightVisionStrength)
                                + nightVisionStrength / nightVisionMultiplier;
                            for (int k = 0; k < 3; k++) {
                                rgb[k] = ColorUtils.clamp(rgb[k] * nightVisionMultiplier);
                            }
                        }
                    }
                }
                if (gamma != 0.0f) {
                    for (int k = 0; k < 3; k++) {
                        float tmp = 1.0f - rgb[k];
                        tmp = 1.0f - tmp * tmp * tmp * tmp;
                        rgb[k] = gamma * tmp + (1.0f - gamma) * rgb[k];
                    }
                }
                mapRGB[s * LIGHTMAP_SIZE + t] = 0xff000000 | ColorUtils.float3ToInt(rgb);
            }
        }
        return true;
    }

    private static void interpolate(int[] map, int offset1, float x, float[] rgb, int offset2) {
        int x0 = (int) Math.floor(x);
        int x1 = (int) Math.ceil(x);
        if (x0 == x1) {
            ColorUtils.intToFloat3(map[offset1 + x0], rgb, offset2);
        } else {
            float xf = x - x0;
            float xg = 1.0f - xf;
            float[] rgb0 = new float[3];
            float[] rgb1 = new float[3];
            ColorUtils.intToFloat3(map[offset1 + x0], rgb0);
            ColorUtils.intToFloat3(map[offset1 + x1], rgb1);
            for (int i = 0; i < 3; i++) {
                rgb[offset2 + i] = xg * rgb0[i] + xf * rgb1[i];
            }
        }
    }
}
