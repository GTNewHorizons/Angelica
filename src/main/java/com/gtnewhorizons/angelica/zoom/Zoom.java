package com.gtnewhorizons.angelica.zoom;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MathHelper;

public class Zoom {

    public static final float ZOOM_MIN = 1.0F;
    public static final float ZOOM_MAX = 64.0F;
    public static final float ZOOM_STEP = 1.2F;
    public static final float ZOOM_DEFAULT = 4.0F;

    @Getter
    private static float zoom = ZOOM_DEFAULT;
    @Getter
    private static final KeyBinding zoomKey = new KeyBinding("Zoom", 0, "key.categories.misc");

    private static boolean zoomEnabled = false;

    public static void init() {
        ClientRegistry.registerKeyBinding(zoomKey);
        FMLCommonHandler.instance().bus().register(new Zoom());
    }

    public static boolean isZoomedIn() {
        return zoomEnabled;
    }

    public static void modifyZoom(int eventDWheel) {
        if (eventDWheel == 0) return;
        zoom = MathHelper.clamp_float((float) (zoom * Math.pow(ZOOM_STEP, Integer.signum(eventDWheel))), ZOOM_MIN, ZOOM_MAX);
    }

    private static void resetMouseFilters(Minecraft mc) {
        ((IMouseFilterExt) mc.entityRenderer.mouseFilterXAxis).angelica$reset();
        ((IMouseFilterExt) mc.entityRenderer.mouseFilterYAxis).angelica$reset();
    }

    public static void resetZoom() {
        final Minecraft mc = Minecraft.getMinecraft();

        zoomEnabled = false;
        zoom = ZOOM_DEFAULT;
        resetMouseFilters(mc);
    }

    @SubscribeEvent
    public void onKeyPress(InputEvent.KeyInputEvent e) {
        if (zoomKey.getKeyCode() == 0) return;

        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return;

        if (zoomKey.getIsKeyPressed()) {
            zoomEnabled = true;
        } else if (zoomEnabled) {
            resetZoom();
        }
    }
}
