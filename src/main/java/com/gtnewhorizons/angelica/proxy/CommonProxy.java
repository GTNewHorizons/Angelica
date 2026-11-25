package com.gtnewhorizons.angelica.proxy;

import com.gtnewhorizons.angelica.common.BlockClipPlaneTest;
import com.gtnewhorizons.angelica.common.BlockIsbrhTest;
import com.gtnewhorizons.angelica.common.BlockTesrTranslucentTest;
import com.gtnewhorizons.angelica.common.BlockTest;
import com.gtnewhorizons.angelica.common.TileEntityClipPlaneTest;
import com.gtnewhorizons.angelica.common.TileTesrTranslucentTest;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.tileentity.TileEntity;

import java.util.function.Supplier;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        if (AngelicaConfig.enableTestBlocks) {
            GameRegistry.registerBlock(new BlockTest(), "test_block");
            GameRegistry.registerBlock(new BlockIsbrhTest(), "test_isbrh");
            GameRegistry.registerBlock(new BlockClipPlaneTest(), "test_clip_plane");
            GameRegistry.registerTileEntity(TileEntityClipPlaneTest.class, "angelica:test_clip_plane");
            registerTesrTest("test_tesr_provider", TileTesrTranslucentTest.Provider.class, TileTesrTranslucentTest.Provider::new);
            registerTesrTest("test_tesr_model", TileTesrTranslucentTest.Model.class, TileTesrTranslucentTest.Model::new);
            registerTesrTest("test_tesr_decal", TileTesrTranslucentTest.Decal.class, TileTesrTranslucentTest.Decal::new);
            registerTesrTest("test_tesr_glow", TileTesrTranslucentTest.Glow.class, TileTesrTranslucentTest.Glow::new);
        }
    }

    private static void registerTesrTest(String name, Class<? extends TileEntity> tile, Supplier<TileEntity> factory) {
        GameRegistry.registerBlock(new BlockTesrTranslucentTest(factory), name);
        GameRegistry.registerTileEntity(tile, "angelica:" + name);
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    // Only present on the client!
    public void putFrametime(long time) { throw new UnsupportedOperationException(); }

    public void putTicktime(long time) { throw new UnsupportedOperationException(); }
}
