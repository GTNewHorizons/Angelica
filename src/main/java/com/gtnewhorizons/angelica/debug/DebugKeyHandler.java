package com.gtnewhorizons.angelica.debug;

import com.gtnewhorizon.gtnhlib.GTNHLib;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasDebugScreenHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/** F3+V toggles {@link AngelicaConfig#verboseF3} */
public class DebugKeyHandler {

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (!Keyboard.getEventKeyState() || Keyboard.getEventKey() != Keyboard.KEY_V || !Keyboard.isKeyDown(Keyboard.KEY_F3)
                || Minecraft.getMinecraft().currentScreen != null) {
            return;
        }

        AngelicaConfig.verboseF3 = !AngelicaConfig.verboseF3;
        ConfigurationManager.save(AngelicaConfig.class);
        CeleritasDebugScreenHandler.invalidate();
        GTNHLib.proxy.addDebugToChat(StatCollector.translateToLocal(AngelicaConfig.verboseF3 ? "angelica.debug.verbose.on" : "angelica.debug.verbose.off"));
    }
}
