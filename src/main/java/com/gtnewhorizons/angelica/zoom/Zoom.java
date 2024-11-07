package com.gtnewhorizons.angelica.zoom;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import lombok.Getter;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

public class Zoom {

    @Getter
    private static float zoom = 4.0F;
    @Getter
    private static final KeyBinding zoomKey = new KeyBinding("Zoom", 0, "key.categories.misc");

    public static void init() {
        ClientRegistry.registerKeyBinding(zoomKey);
        FMLCommonHandler.instance().bus().register(new Zoom());
    }

    public static void modifyZoom(int eventDWheel) {
        if (eventDWheel == 0) return;
        zoom = MathHelper.clamp_float(zoom + Integer.signum(eventDWheel), 1.0F, 64.0F);
    }

    @SubscribeEvent
    public void onKeyPress(InputEvent.KeyInputEvent e) {
        final int keyCode = zoomKey.getKeyCode();
        if (keyCode != 0 && Keyboard.getEventKey() == keyCode && !Keyboard.getEventKeyState()) {
            zoom = 4.0F;
        }
    }
}
