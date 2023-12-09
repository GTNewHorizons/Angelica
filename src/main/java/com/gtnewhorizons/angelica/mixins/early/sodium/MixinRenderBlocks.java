package com.gtnewhorizons.angelica.mixins.early.sodium;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderBlocks.class)
public abstract class MixinRenderBlocks {
    @Shadow
    public abstract boolean renderStandardBlockWithColorMultiplier(Block block, int x, int y, int z, float f, float f1, float f2);

    /**
     * @author mitchej123
     * @reason Let sodium handle AO
     */
    @Overwrite
    public boolean renderStandardBlockWithAmbientOcclusion(Block block, int x, int y, int z, float f, float f1, float f2) {
        return renderStandardBlockWithColorMultiplier(block, x, y, z, f, f1, f2);
    }

    /**
     * @author mitchej123
     * @reason Let sodium handle AO
     */
    @Overwrite
    public boolean renderStandardBlockWithAmbientOcclusionPartial(Block block, int x, int y, int z, float f, float f1, float f2) {
        return renderStandardBlockWithColorMultiplier(block, x, y, z, f, f1, f2);
    }



}
