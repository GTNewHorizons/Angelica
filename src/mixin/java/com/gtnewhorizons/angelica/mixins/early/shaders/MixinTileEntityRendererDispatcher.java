package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.prupe.mcpatcher.ctm.CTMUtils;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
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

        final int id = BlockRenderingSettings.INSTANCE.resolveBlockId(block, te.getBlockMetadata());
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(Math.max(0, id));
    }

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", at = @At("RETURN"))
    private void iris$resetBlockEntityId(TileEntity te, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        CTMUtils.clearCurrentCompact();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
    }
}
