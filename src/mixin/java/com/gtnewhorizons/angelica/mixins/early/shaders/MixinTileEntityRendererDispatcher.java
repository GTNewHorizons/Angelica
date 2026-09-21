package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.tesr.PassRebindGate;
import com.gtnewhorizons.angelica.rendering.tesr.TesrAttribution;
import com.prupe.mcpatcher.ctm.CTMUtils;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.uniforms.CapturedRenderingState;
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
        GbufferPrograms.pushBlendState();
        CTMUtils.clearCurrentCompact();
        CapturedRenderingState.INSTANCE.pushCurrentBlockEntity();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(te == null ? null : te.getBlockType(), te == null ? 0 : te.getBlockMetadata());
        TesrAttribution.currentRenderable = te != null ? te.getClass() : null;

        // Rebind Iris's pass in case the previous TESR changed it
        PassRebindGate.rebindIfDirty();
    }

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", at = @At("RETURN"))
    private void iris$resetBlockEntityId(TileEntity te, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        CTMUtils.clearCurrentCompact();
        CapturedRenderingState.INSTANCE.popCurrentBlockEntity();
        TesrAttribution.currentRenderable = null;
        GbufferPrograms.popBlendStateTop();
    }
}
