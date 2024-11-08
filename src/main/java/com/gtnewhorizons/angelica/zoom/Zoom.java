package com.gtnewhorizons.angelica.zoom;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

public class Zoom {

    @Getter
    private static float zoom = 4.0F;
    @Getter
    private static final KeyBinding zoomKey = new KeyBinding("Zoom", 0, "key.categories.misc");

    public static final float ZOOM_MIN = 1.0F;
    public static final float ZOOM_MAX = 64.0F;
    public static final float ZOOM_STEP = 1.2F;

    private static boolean lastSmoothCameraState = false;

    public static void init() {
        ClientRegistry.registerKeyBinding(zoomKey);
        FMLCommonHandler.instance().bus().register(new Zoom());
    }

    public static void modifyZoom(int eventDWheel) {
        if (eventDWheel == 0) return;
        zoom = MathHelper.clamp_float((float) (zoom * Math.pow(ZOOM_STEP, Integer.signum(eventDWheel))), ZOOM_MIN, ZOOM_MAX);
    }

    @SubscribeEvent
    public void onKeyPress(InputEvent.KeyInputEvent e) {
        final int keyCode = zoomKey.getKeyCode();
        if (keyCode == 0 || Keyboard.getEventKey() != keyCode || Keyboard.isRepeatEvent()) return;
        final Minecraft mc = Minecraft.getMinecraft();
        if (Keyboard.getEventKeyState()) {
            lastSmoothCameraState = mc.gameSettings.smoothCamera;
            mc.gameSettings.smoothCamera = true;
        } else {
            zoom = 4.0F;
            if (mc.gameSettings.smoothCamera != lastSmoothCameraState) {
                ((IMouseFilterExt) mc.entityRenderer.mouseFilterXAxis).angelica$reset();
                ((IMouseFilterExt) mc.entityRenderer.mouseFilterYAxis).angelica$reset();
            }
            mc.gameSettings.smoothCamera = lastSmoothCameraState;
        }
    }
}
