package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.tesr.PassRebindGate;
import com.gtnewhorizons.angelica.rendering.tesr.TesrAttribution;
import com.gtnewhorizons.angelica.rendering.tesr.TesrProviderDispatch;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(iris$resolveBlockEntityId(te));
        TesrAttribution.currentRenderable = te != null ? te.getClass() : null;

        // Rebind Iris's pass in case the previous TESR changed it
        PassRebindGate.rebindIfDirty();
    }

    private static int iris$resolveBlockEntityId(TileEntity te) {
        return TesrProviderDispatch.resolveBlockEntityId(te);
    }

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", at = @At("RETURN"))
    private void iris$resetBlockEntityId(TileEntity te, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        CTMUtils.clearCurrentCompact();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
        TesrAttribution.currentRenderable = null;
    }
}
