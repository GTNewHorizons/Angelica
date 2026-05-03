package com.gtnewhorizons.angelica;

import com.gtnewhorizons.angelica.proxy.CommonProxy;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.gtnewhorizons.angelica.AngelicaMod.MOD_ID;

@Mod(
        modid = MOD_ID,
        name = "Angelica",
        version = Tags.VERSION,
        dependencies = """
            required-after:gtnhlib@[0.8.21,);\
            before:lwjgl3ify@[1.5.3,);\
            after:hodgepodge@[2.4.4,);\
            after:CodeChickenCore@[1.2.0,);\
            after:archaicfix@[0.7.0,);\
            """,
        acceptedMinecraftVersions = "[1.7.10]",
        acceptableRemoteVersions = "*",
        guiFactory = "com.gtnewhorizons.angelica.config.AngelicaGuiConfigFactory")
public class AngelicaMod {

    public static final String MOD_ID = "angelica";
    public static final Logger LOGGER = LogManager.getLogger("Angelica");
    public static final boolean lwjglDebug = Boolean.getBoolean("org.lwjgl.util.Debug");

    @SidedProxy(
        clientSide = "com.gtnewhorizons.angelica.proxy.ClientProxy",
        serverSide = "com.gtnewhorizons.angelica.proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }
}
