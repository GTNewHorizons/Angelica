package com.gtnewhorizons.angelica.zoom;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import jss.notfine.core.Settings;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Mouse;

public class Zoom {

    public static final float ZOOM_MIN = 1.0F;
    public static final float ZOOM_MAX = 64.0F;
    public static final float ZOOM_STEP = 1.2F;
    public static final float ZOOM_DEFAULT = 4.0F;

    @Getter
    private static float zoom = ZOOM_DEFAULT;

    public static final float ZOOM_SMOOTH_SNAP_EPSILON = 0.001F;

    @Getter
    private static float currentZoom = 1.0F;
    private static long zoomLerpLastNano = 0L;

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

    public static void updateZoomLerp() {
        final long now = System.nanoTime();
        float dt = zoomLerpLastNano == 0L ? 0.0F : (now - zoomLerpLastNano) / 1.0e9F;
        zoomLerpLastNano = now;

        final float target = zoomEnabled ? zoom : 1.0F;

        if (dt <= 0.0F || !(boolean) Settings.ZOOM_SMOOTH.option.getStore()) {
            currentZoom = target;
            return;
        }

        // Clamp dt so a long frame (alt-tab, GC pause) does not snap the zoom.
        dt = Math.min(dt, 0.1F);

        final int rate = (int) Settings.ZOOM_SMOOTH_SPEED.option.getStore();
        currentZoom += (target - currentZoom) * (1.0F - (float) Math.exp(-dt * rate));

        if (Math.abs(target - currentZoom) < ZOOM_SMOOTH_SNAP_EPSILON) {
            currentZoom = target;
        }
    }

    private static void resetMouseFilters(Minecraft mc) {
        if (mc.entityRenderer != null) {
            ((IMouseFilterExt) mc.entityRenderer.mouseFilterXAxis).angelica$reset();
            ((IMouseFilterExt) mc.entityRenderer.mouseFilterYAxis).angelica$reset();
        }
    }

    public static void resetZoom() {
        final Minecraft mc = Minecraft.getMinecraft();

        zoomEnabled = false;
        zoom = ZOOM_DEFAULT;
        resetMouseFilters(mc);
    }

    private void handleKeyEvent() {
        if (zoomKey.getKeyCode() == 0) return;

        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return;

        if (zoomKey.getIsKeyPressed()) {
            zoomEnabled = true;
        } else if (zoomEnabled) {
            resetZoom();
        }
    }

    @SubscribeEvent
    public void onKeyPress(InputEvent.KeyInputEvent e) {
        handleKeyEvent();
    }

    @SubscribeEvent
    public void onMouseKeyPress(InputEvent.MouseInputEvent e) {
        // MouseInputEvent is also fired on mouse movement, which we don't care about, only mouse button presses, so
        // ensure that before any further key processing.
        if (Mouse.getEventButton() >= 0) {
            handleKeyEvent();
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        updateZoomLerp();
    }
}
