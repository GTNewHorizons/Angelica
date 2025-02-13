package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.coderbot.iris.api.IIrisRenderBlocks;

@Mixin(RenderBlocks.class)
public class MixinRenderBlocks implements IIrisRenderBlocks {

    @Unique
    private int subpass;

    @Unique
    private ChunkBuildBuffers buffers;

    @Override
    public void setCurrentSubpass(int subpass) {
        this.subpass = subpass;
    }

    @Override
    public void setBuffers(ChunkBuildBuffers buffers) {
        this.buffers = buffers;
    }

    @Override
    public int getCurrentSubpass() {
        return subpass;
    }

    @Override
    public void setShaderMaterialId(Block block, int meta) {
        if (buffers != null) {
            buffers.iris$setMaterialId(block, meta);
        }
    }
}
