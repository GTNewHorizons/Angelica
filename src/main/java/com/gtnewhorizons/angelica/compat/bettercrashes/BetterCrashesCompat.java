package com.gtnewhorizons.angelica.compat.bettercrashes;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.IRenderGlobalExt;
import cpw.mods.fml.common.Optional;
import net.minecraft.client.Minecraft;
import vfyjxf.bettercrashes.utils.StateManager;

import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.LOGGER;

@Optional.Interface(modid = "angelica", iface = "vfyjxf.bettercrashes.utils.StateManager.IResettable")
public class BetterCrashesCompat implements StateManager.IResettable {
    private static BetterCrashesCompat INSTANCE;

    public static void init() {
        if (INSTANCE == null) {
            INSTANCE = new BetterCrashesCompat();
        }
    }


    public BetterCrashesCompat() {
        LOGGER.info("BetterCrashesCompat initialized");
        this.register();
    }

    @Override
    public void resetState() {
        LOGGER.info("Reloading SodiumRenderer");
        ((IRenderGlobalExt) Minecraft.getMinecraft().renderGlobal).reload();
        LOGGER.info("Resetting GLStateManager");
        GLStateManager.reset();
    }
}
