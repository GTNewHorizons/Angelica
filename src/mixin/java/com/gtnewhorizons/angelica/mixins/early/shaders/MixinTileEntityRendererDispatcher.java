package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.prupe.mcpatcher.ctm.CTMUtils;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcher {

    /**
     * Keep one renderer's blend state out of the next one's program choice.
     */
    @WrapMethod(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V")
    private void iris$isolateBlendState(TileEntity te, double x, double y, double z, float partialTicks,
                                        Operation<Void> original) {
        final int saved = GbufferPrograms.pushBlendState();
        try {
            original.call(te, x, y, z, partialTicks);
        } finally {
            GbufferPrograms.popBlendState(saved);
        }
    }

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", at = @At("HEAD"))
    private void iris$setBlockEntityId(TileEntity te, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        CTMUtils.clearCurrentCompact();
        if (te == null) {
            CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
            return;
        }
        Block block = te.getBlockType();
        if (block == null) {
            CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
            return;
        }

        Reference2ObjectMap<Block, Int2IntMap> blockMetaMatches = BlockRenderingSettings.INSTANCE.getBlockMetaMatches();
        if (blockMetaMatches == null) {
            CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
            return;
        }

        Int2IntMap metaMap = blockMetaMatches.get(block);
        if (metaMap == null) {
            CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
            return;
        }

        int meta = te.getBlockMetadata();
        int id = metaMap.get(meta);
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(Math.max(0, id));
    }

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", at = @At("RETURN"))
    private void iris$resetBlockEntityId(TileEntity te, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        CTMUtils.clearCurrentCompact();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
    }
}
