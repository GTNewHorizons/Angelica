package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.api.ThreadSafeISBRH;
import com.gtnewhorizons.angelica.common.BlockIsbrhTest;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

@ThreadSafeISBRH(perThread = false)
public class IsbrhTestRenderer implements ISimpleBlockRenderingHandler {

    private static volatile boolean crashTestArmed;

    public static void armCrashTest() {
        crashTestArmed = true;
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {}

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks renderer) {
        if (crashTestArmed) {
            crashTestArmed = false;
            throw new IllegalStateException("angelica isbrh crashtest");
        }
        return renderer.renderStandardBlock(block, x, y, z);
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return false;
    }

    @Override
    public int getRenderId() {
        return BlockIsbrhTest.renderId;
    }
}
