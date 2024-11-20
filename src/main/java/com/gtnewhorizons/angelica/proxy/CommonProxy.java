package com.gtnewhorizons.angelica.proxy;

import com.gtnewhorizons.angelica.common.BlockTest;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {

        if (AngelicaConfig.enableTestBlocks){
            GameRegistry.registerBlock(new BlockTest(), "test_block");
        }
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    // Only present on the client!
    public void putFrametime(long time) { throw new UnsupportedOperationException(); }

    public void putTicktime(long time) { throw new UnsupportedOperationException(); }
}
