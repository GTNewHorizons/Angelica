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

    public static int minimumFarPlaneDistance;
    public static double cloudTranslucencyCheck;
    public static boolean shadows;
    public static boolean droppedItemDetail;
    public static boolean leavesOpaque;
    public static boolean waterDetail;
    public static boolean vignette;
    public static byte downfallDistance;

    //TODO: Hook up using sodium system
    public static double entityRenderScaleFactor = 20000;

    public static ResourceLocation defaultBackground = Gui.optionsBackground;
    public static ResourceLocation[] extraBackgrounds = new ResourceLocation[] {
        new ResourceLocation("textures/blocks/sand.png"),
        new ResourceLocation("textures/blocks/mycelium_top.png"),
        new ResourceLocation("textures/blocks/stonebrick.png"),
        new ResourceLocation("textures/blocks/stonebrick_mossy.png"),
        new ResourceLocation("textures/blocks/planks_oak.png"),
        new ResourceLocation("textures/blocks/planks_birch.png")
    };

    public static void backgroundUpdated() {
        int value = ((BackgroundSelect)Settings.GUI_BACKGROUND.option.getStore()).ordinal();
        if(value == 0) {
            Gui.optionsBackground = defaultBackground;
        } else {
            Gui.optionsBackground = extraBackgrounds[((BackgroundSelect)Settings.GUI_BACKGROUND.option.getStore()).ordinal() - 1];
        }
    }

    public static void cloudsUpdated() {
        if(Settings.MODE_CLOUDS.option.getStore() != GraphicsQualityOff.OFF) {
            minimumFarPlaneDistance = 32 * (int)Settings.RENDER_DISTANCE_CLOUDS.option.getStore();
            minimumFarPlaneDistance += Math.abs((int)Settings.CLOUD_HEIGHT.option.getStore());
            mc.gameSettings.clouds = true;
        } else {
            minimumFarPlaneDistance = 128;
            mc.gameSettings.clouds = false;
        }
        switch((AlwaysNever)Settings.MODE_CLOUD_TRANSLUCENCY.option.getStore()) {
            case DEFAULT -> cloudTranslucencyCheck = (int)Settings.CLOUD_HEIGHT.option.getStore();
            case ALWAYS -> cloudTranslucencyCheck = Double.NEGATIVE_INFINITY;
            case NEVER -> cloudTranslucencyCheck = Double.POSITIVE_INFINITY;
        }
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
        //Do not re-enable, see MixinBlockLeaves workaround for Angelica/Sodium style menus.
        //mc.renderGlobal.loadRenderers();
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
