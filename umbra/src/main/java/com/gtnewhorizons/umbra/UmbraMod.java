package com.gtnewhorizons.umbra;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import com.gtnewhorizons.umbra.proxy.CommonProxy;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = UmbraMod.MOD_ID, name = "Umbra", version = Tags.VERSION,
    dependencies = "required-after:gtnhlib@[0.8.21,);before:lwjgl3ify@[1.5.3,)",
    acceptableRemoteVersions = "*")
public class UmbraMod {

    public static final String MOD_ID = "umbra";
    public static final Logger LOGGER = LogManager.getLogger("Umbra");

    @SidedProxy(
        clientSide = "com.gtnewhorizons.umbra.proxy.ClientProxy",
        serverSide = "com.gtnewhorizons.umbra.proxy.CommonProxy")
    public static CommonProxy proxy;

    private static boolean isDisabled() {
        return Launch.blackboard != null && Boolean.TRUE.equals(Launch.blackboard.get("umbra.disabled"));
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (isDisabled()) {
            LOGGER.info("Umbra is disabled (Angelica detected), skipping preInit");
            return;
        }
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (isDisabled()) return;
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (isDisabled()) return;
        proxy.postInit(event);
    }
}
