package jss.notfine.core;

import jss.notfine.config.VideoSettings;
import jss.notfine.gui.options.named.AlwaysNever;
import jss.notfine.gui.options.named.BackgroundSelect;
import jss.notfine.gui.options.named.DownfallQuality;
import jss.notfine.gui.options.named.GraphicsQualityOff;
import jss.notfine.gui.options.named.GraphicsToggle;
import jss.notfine.gui.options.named.LeavesQuality;
import me.jellysquid.mods.sodium.client.gui.options.named.GraphicsQuality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.init.Blocks;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;

import java.io.File;

public class SettingsManager {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public static VideoSettings settingsFile = new VideoSettings(
        new File(Launch.minecraftHome + File.separator + "optionsnf.txt")
    );

    public static final float VANILLA_CLOUD_HEIGHT = 128.0f;

    public static int minimumFarPlaneDistance;
    public static float cloudHeightOffset;
    public static AlwaysNever cloudTranslucencyMode = AlwaysNever.DEFAULT;
    public static boolean shadows;
    public static boolean droppedItemDetail;
    public static boolean leavesOpaque;
    public static boolean waterDetail;
    public static boolean vignette;
    public static byte downfallDistance;

    //TODO: Hook up using sodium system
    public static double entityRenderScaleFactor = 20000;

    public static void backgroundUpdated() {
        Gui.optionsBackground = ((BackgroundSelect)Settings.GUI_BACKGROUND.option.getStore()).getTexture();
    }

    public static void cloudsUpdated() {
        cloudHeightOffset = (int)Settings.CLOUD_HEIGHT.option.getStore() - VANILLA_CLOUD_HEIGHT;
        if(Settings.MODE_CLOUDS.option.getStore() != GraphicsQualityOff.OFF) {
            minimumFarPlaneDistance = 32 * ((int)Settings.RENDER_DISTANCE_CLOUDS.option.getStore());
            mc.gameSettings.clouds = true;
        } else {
            minimumFarPlaneDistance = 128;
            mc.gameSettings.clouds = false;
        }
        cloudTranslucencyMode = (AlwaysNever)Settings.MODE_CLOUD_TRANSLUCENCY.option.getStore();
    }

    public static float currentCloudHeight() {
        return mc.theWorld == null ? VANILLA_CLOUD_HEIGHT : mc.theWorld.provider.getCloudHeight();
    }

    public static double cloudRenderOrderHeight() {
        return switch(cloudTranslucencyMode) {
            case ALWAYS -> Double.NEGATIVE_INFINITY;
            case NEVER -> Double.POSITIVE_INFINITY;
            default -> currentCloudHeight();
        };
    }

    public static void downfallDistanceUpdated() {
        switch((DownfallQuality)Settings.DOWNFALL_DISTANCE.option.getStore()) {
            case DEFAULT -> downfallDistance = (byte) (mc.gameSettings.fancyGraphics ? 10 : 5);
            case FAST -> downfallDistance = (byte) 5;
            case FANCY -> downfallDistance = (byte) 10;
            case ULTRA -> downfallDistance = (byte) 15;
            case OFF -> downfallDistance = (byte) 0;
        }
    }

    public static void leavesUpdated() {
        LeavesQuality value = (LeavesQuality)Settings.MODE_LEAVES.option.getStore();
        leavesOpaque = value == LeavesQuality.FAST || (value == LeavesQuality.DEFAULT && !mc.gameSettings.fancyGraphics);
        Blocks.leaves.setGraphicsLevel(!leavesOpaque);
        Blocks.leaves2.setGraphicsLevel(!leavesOpaque);
    }

    public static void shadowsUpdated() {
        switch((GraphicsToggle)Settings.MODE_SHADOWS.option.getStore()) {
            case DEFAULT -> shadows = mc.gameSettings.fancyGraphics;
            case ON-> shadows = true;
            case OFF -> shadows = false;
        }
    }

    public static void droppedItemDetailUpdated() {
        switch((GraphicsQuality)Settings.MODE_DROPPED_ITEMS.option.getStore()) {
            case DEFAULT -> droppedItemDetail = mc.gameSettings.fancyGraphics;
            case FANCY -> droppedItemDetail = true;
            case FAST -> droppedItemDetail = false;
        }
    }

    public static void waterDetailUpdated() {
        switch((GraphicsQuality)Settings.MODE_WATER.option.getStore()) {
            case DEFAULT -> waterDetail = mc.gameSettings.fancyGraphics;
            case FANCY -> waterDetail = true;
            case FAST -> waterDetail = false;
        }
    }

    public static void vignetteUpdated() {
        switch((GraphicsToggle)Settings.MODE_VIGNETTE.option.getStore()) {
            case DEFAULT -> vignette = mc.gameSettings.fancyGraphics;
            case ON -> vignette = true;
            case OFF -> vignette = false;
        }
    }

    public static void graphicsUpdated() {
        downfallDistanceUpdated();
        leavesUpdated();
        shadowsUpdated();
        droppedItemDetailUpdated();
        waterDetailUpdated();
        vignetteUpdated();
    }

}
