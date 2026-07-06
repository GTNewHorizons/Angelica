package com.gtnewhorizons.angelica.mixins.late.client.extrautils;

import com.rwtema.extrautils.block.render.FakeRenderEtherealBlocks;
import com.rwtema.extrautils.block.render.RenderBlockConnectedTextures;
import com.rwtema.extrautils.block.render.RenderBlockConnectedTexturesEthereal;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RenderBlockConnectedTexturesEthereal.class)
public abstract class MixinRenderBlockConnectedTexturesEthereal extends RenderBlockConnectedTextures{

    @Unique
    private FakeRenderEtherealBlocks newFakeRenderEthereal = new FakeRenderEtherealBlocks();

    /**
     * @author Cleptomania
     * @reason Thread safety fixes for ethereal connected textures. The way the subclassing
     *          works for this system means overwriting this function to use our new non-static
     *          FakeRenderEtherealBlocks field is about the only way to do it.
     */
    @Override
    @Overwrite(remap = false)
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks renderer) {
        if (renderer.hasOverrideBlockTexture())
            return renderer.renderStandardBlock(block, x, y, z);
        getFakeRender().setWorld(renderer.blockAccess);
        getFakeRender().curBlock = world.getBlock(x, y, z);
        getFakeRender().curMeta = world.getBlockMetadata(x, y, z);
        block.setBlockBoundsBasedOnState(getFakeRender().blockAccess, x, y, z);
        getFakeRender().setRenderBoundsFromBlock(block);
        boolean render = getFakeRender().renderStandardBlock(block, x, y, z);
        newFakeRenderEthereal.setWorld(renderer.blockAccess);
        newFakeRenderEthereal.curBlock = getFakeRender().curBlock;
        newFakeRenderEthereal.curMeta = getFakeRender().curMeta;
        double h = 0.05D;
        newFakeRenderEthereal.setRenderBounds(h, h, h, 1.0D - h, 1.0D - h, 1.0D - h);
        render &= newFakeRenderEthereal.renderStandardBlock(block, x, y, z);
        return render;
    }
}
