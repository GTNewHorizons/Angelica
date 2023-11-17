package com.gtnewhorizons.angelica;

import com.gtnewhorizons.angelica.proxy.CommonProxy;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
        modid = "angelica",
        name = "Angelica",
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.7.10]",
        acceptableRemoteVersions = "*")
public class AngelicaMod {
    @SidedProxy(clientSide = "com.gtnewhorizons.angelica.proxy.ClientProxy", serverSide = "com.gtnewhorizons.angelica.proxy.CommonProxy")
    public static CommonProxy proxy;


    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
