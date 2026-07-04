package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.tesr.TesrProviderDispatch;
import com.prupe.mcpatcher.ctm.CTMUtils;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
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
        CTMUtils.clearCurrentCompact();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(iris$resolveBlockEntityId(te));

        // Rebind Iris's pass in case the previous TESR changed it
        if (Iris.enabled) {
            final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
            if (pipeline != null) {
                pipeline.rebindCurrentPass();
            }
        }
    }

    private static int iris$resolveBlockEntityId(TileEntity te) {
        return TesrProviderDispatch.resolveBlockEntityId(te);
    }

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", at = @At("RETURN"))
    private void iris$resetBlockEntityId(TileEntity te, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        CTMUtils.clearCurrentCompact();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
    }
}
