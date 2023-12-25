package com.prupe.mcpatcher.cc;

import java.util.Arrays;
import java.util.Random;

import net.minecraft.util.ResourceLocation;

import com.prupe.mcpatcher.MCPatcherUtils;
import com.prupe.mcpatcher.mal.biome.BiomeAPI;
import com.prupe.mcpatcher.mal.biome.ColorUtils;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;

public class ColorizeEntity {

    private static final ResourceLocation LAVA_DROP_COLORS = TexturePackAPI
        .newMCPatcherResourceLocation("colormap/lavadrop.png");
    private static final ResourceLocation MYCELIUM_COLORS = TexturePackAPI
        .newMCPatcherResourceLocation("colormap/myceliumparticle.png");
    private static final ResourceLocation XPORB_COLORS = TexturePackAPI
        .newMCPatcherResourceLocation("colormap/xporb.png");

    static float[] waterBaseColor; // particle.water
    private static float[] lavaDropColors; // misc/lavadropcolor.png

    public static float[] portalColor = new float[] { 1.0f, 0.3f, 0.9f };

    private static final Random random = new Random();
    private static int[] myceliumColors;

    private static int[] xpOrbColors;
    public static int xpOrbRed;
    public static int xpOrbGreen;
    public static int xpOrbBlue;

    private static final String[] colorNames = new String[] { "white", "orange", "magenta", "lightBlue", "yellow",
        "lime", "pink", "gray", "silver", "cyan", "purple", "blue", "brown", "green", "red", "black", };

    private static final Integer[] dyeColors = new Integer[colorNames.length]; // dye.*
    private static final float[][] fleeceColors = new float[colorNames.length][]; // sheep.*
    private static final float[][] collarColors = new float[colorNames.length][]; // collar.*
    private static final float[][] armorColors = new float[colorNames.length][]; // armor.*

    public static int undyedLeatherColor; // armor.default

    static {
        try {
            reset();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    static void reset() {
        waterBaseColor = new float[] { 0.2f, 0.3f, 1.0f };
        portalColor = new float[] { 1.0f, 0.3f, 0.9f };
        lavaDropColors = null;
        Arrays.fill(dyeColors, null);
        Arrays.fill(fleeceColors, null);
        Arrays.fill(collarColors, null);
        Arrays.fill(armorColors, null);
        undyedLeatherColor = 0xa06540;
        myceliumColors = null;
        xpOrbColors = null;
    }

    static void reloadParticleColors(PropertiesFile properties) {
        Colorizer.loadFloatColor("drop.water", waterBaseColor);
        Colorizer.loadFloatColor("particle.water", waterBaseColor);
        Colorizer.loadFloatColor("particle.portal", portalColor);
        int[] rgb = MCPatcherUtils.getImageRGB(TexturePackAPI.getImage(LAVA_DROP_COLORS));
        if (rgb != null) {
            lavaDropColors = new float[3 * rgb.length];
            for (int i = 0; i < rgb.length; i++) {
                ColorUtils.intToFloat3(rgb[i], lavaDropColors, 3 * i);
            }
        }
        myceliumColors = MCPatcherUtils.getImageRGB(TexturePackAPI.getImage(MYCELIUM_COLORS));
    }

    static void reloadDyeColors(PropertiesFile properties) {
        for (int i = 0; i < colorNames.length; i++) {
            dyeColors[i] = Colorizer.loadIntegerColor("dye." + Colorizer.getStringKey(colorNames, i));
        }
        for (int i = 0; i < colorNames.length; i++) {
            String key = Colorizer.getStringKey(colorNames, i);
            fleeceColors[i] = Colorizer.loadFloatColor("sheep." + key);
            collarColors[i] = Colorizer.loadFloatColor("collar." + key);
            armorColors[i] = Colorizer.loadFloatColor("armor." + key);
        }
        undyedLeatherColor = Colorizer.loadIntColor("armor.default", undyedLeatherColor);
    }

    static void reloadXPOrbColors(PropertiesFile properties) {
        xpOrbColors = MCPatcherUtils.getImageRGB(TexturePackAPI.getImage(XPORB_COLORS));
    }

    public static int colorizeXPOrb(int origColor, float timer) {
        if (xpOrbColors == null || xpOrbColors.length == 0) {
            return origColor;
        } else {
            return xpOrbColors[(int) ((Math.sin(timer / 4.0) + 1.0) * (xpOrbColors.length - 1) / 2.0)];
        }
    }

    public static int colorizeXPOrb(int origRed, int origBlue, float timer) {
        int color = colorizeXPOrb((origRed << 16) | 255 | origBlue, timer);
        xpOrbRed = (color >> 16) & 0xff;
        xpOrbGreen = (color >> 8) & 0xff;
        xpOrbBlue = color & 0xff;
        return xpOrbRed;
    }

    public static boolean computeLavaDropColor(int age) {
        if (lavaDropColors == null) {
            return false;
        } else {
            int offset = 3 * Math.max(Math.min(lavaDropColors.length / 3 - 1, age - 20), 0);
            System.arraycopy(lavaDropColors, offset, Colorizer.setColor, 0, 3);
            return true;
        }
    }

    public static boolean computeMyceliumParticleColor() {
        if (myceliumColors == null) {
            return false;
        } else {
            Colorizer.setColorF(myceliumColors[random.nextInt(myceliumColors.length)]);
            return true;
        }
    }

    public static void computeSuspendColor(int defaultColor, int i, int j, int k) {
        if (ColorizeWorld.underwaterColor != null) {
            defaultColor = ColorizeWorld.underwaterColor.getColorMultiplier(BiomeAPI.getWorld(), i, j, k);
        }
        Colorizer.setColorF(defaultColor);
    }

    public static float[] getWolfCollarColor(float[] rgb, int index) {
        return getArrayColor(collarColors, rgb, index);
    }

    public static float[] getArmorDyeColor(float[] rgb, int index) {
        return getArrayColor(armorColors, rgb, index);
    }

    private static float[] getArrayColor(float[][] array, float[] rgb, int index) {
        float[] newRGB = array[index];
        return newRGB == null ? rgb : newRGB;
    }
}
