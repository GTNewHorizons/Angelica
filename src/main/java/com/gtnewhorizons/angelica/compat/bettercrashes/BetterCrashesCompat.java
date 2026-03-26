package com.gtnewhorizons.angelica.compat.bettercrashes;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.IRenderGlobalExt;
import cpw.mods.fml.common.Optional;
import net.minecraft.client.Minecraft;
import vfyjxf.bettercrashes.utils.StateManager;

@Optional.Interface(modid = "angelica", iface = "vfyjxf.bettercrashes.utils.StateManager.IResettable")
public class BetterCrashesCompat implements StateManager.IResettable {

    private static BetterCrashesCompat INSTANCE;

    public BetterCrashesCompat() {
        AngelicaMod.LOGGER.info("BetterCrashesCompat initialized");
        this.register();
    }

    public static void init() {
        if (INSTANCE == null) {
            INSTANCE = new BetterCrashesCompat();
        }
    }

    @Override
    public void resetState() {
        AngelicaMod.LOGGER.info("Reloading SodiumRenderer");
        ((IRenderGlobalExt) Minecraft.getMinecraft().renderGlobal).angelica$reload();
        AngelicaMod.LOGGER.info("Resetting GLStateManager");
        GLStateManager.reset();
    }
}
