package com.gtnewhorizons.angelica.proxy;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {

        // TODO: JSON models
        /*if (AngelicaConfig.enableTestBlocks){
            GameRegistry.registerBlock(new BlockTest(), "test_block");
        }*/
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}
}
