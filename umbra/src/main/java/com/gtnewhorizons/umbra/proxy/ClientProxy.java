package com.gtnewhorizons.umbra.proxy;

import com.gtnewhorizons.umbra.UmbraMod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        UmbraMod.LOGGER.info("Umbra client preInit - GLSM initialized via mixin");
    }

    @Override
    public void init(FMLInitializationEvent event) {
        UmbraMod.LOGGER.info("Umbra client init");
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
    }
}
