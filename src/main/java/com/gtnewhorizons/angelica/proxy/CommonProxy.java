package com.gtnewhorizons.angelica.proxy;

import com.gtnewhorizons.angelica.common.BlockTest;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.testing.BlockTESR;
import com.gtnewhorizons.angelica.testing.TileTESR;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.ItemBlock;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {

        if (AngelicaConfig.enableTestBlocks){
            GameRegistry.registerBlock(new BlockTest(), "test_block");

            GameRegistry.registerBlock(new BlockTESR(), ItemBlock.class, "block_tesr");
            GameRegistry.registerTileEntity(TileTESR.class, "Test TESR");
        }
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}
}
