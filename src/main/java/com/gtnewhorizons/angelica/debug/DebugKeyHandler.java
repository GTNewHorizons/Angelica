package com.gtnewhorizons.angelica.debug;

import com.gtnewhorizon.gtnhlib.GTNHLib;
import com.gtnewhorizons.angelica.debug.screen.DebugEntryDef;
import com.gtnewhorizons.angelica.debug.screen.DebugScreen;
import com.gtnewhorizons.angelica.debug.screen.DebugScreenEntries;
import com.gtnewhorizons.angelica.mixins.interfaces.IGameSettingsExt;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

public class DebugKeyHandler {

    public static final DebugF3Toggle F3_TOGGLE = new DebugF3Toggle();

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        final int key = Keyboard.getEventKey();
        final Minecraft mc = Minecraft.getMinecraft();

        if (mc.currentScreen != null) {
            F3_TOGGLE.disarm();
            return;
        }

        if (!Keyboard.getEventKeyState()) {
            if (key == Keyboard.KEY_F3 && F3_TOGGLE.onF3Release()) {
                mc.gameSettings.showDebugInfo = !mc.gameSettings.showDebugInfo;
                mc.gameSettings.showDebugProfilerChart = F3_TOGGLE.shift();
                ((IGameSettingsExt) mc.gameSettings).angelica$setShowFpsGraph(F3_TOGGLE.alt());
            }
            if (key == Keyboard.KEY_F3) DebugScreen.syncOverlayVisible();
            return;
        }

        F3_TOGGLE.onKeyPress(key);

        if (!Keyboard.isKeyDown(Keyboard.KEY_F3)) {
            return;
        }
        if (!Keyboard.isRepeatEvent()) {
            switch (key) {
                case Keyboard.KEY_V -> toggleAndReport(DebugScreenEntries.ANGELICA_RENDERER_VERBOSE, "angelica.debug.verbose");
                case Keyboard.KEY_B -> {
                    if (DebugScreen.ENTRIES != null) DebugScreen.ENTRIES.toggleStatus(DebugScreenEntries.ENTITY_HITBOXES);
                }
                default -> {}
            }
        }
        if (key == Keyboard.KEY_B && DebugScreen.ENTRIES != null) {
            RenderManager.debugBoundingBox = DebugScreenEntries.ENTITY_HITBOXES.isEnabled();
        }
    }

    private static void toggleAndReport(DebugEntryDef def, String key) {
        if (DebugScreen.ENTRIES == null) return;
        final boolean on = DebugScreen.ENTRIES.toggleStatus(def);
        GTNHLib.proxy.addDebugToChat(StatCollector.translateToLocal(key + (on ? ".on" : ".off")));
    }
}
