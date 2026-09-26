package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.tesr.PassRebindGate;
import com.gtnewhorizons.angelica.rendering.tesr.TesrAttribution;
import com.gtnewhorizons.angelica.rendering.RenderRecovery;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBlendScope;
import com.gtnewhorizons.angelica.rendering.tesr.TesrProviderDispatch;
import com.prupe.mcpatcher.ctm.CTMUtils;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcher {

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", at = @At("HEAD"))
    private void iris$beginBlockEntity(TileEntity te, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        RenderRecovery.throwIfCrashTestArmed();
        TesrBlendScope.enter();
        CTMUtils.clearCurrentCompact();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(iris$resolveBlockEntityId(te));
        TesrAttribution.currentRenderable = te != null ? te.getClass() : null;
        PassRebindGate.rebindIfDirty();
    }

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", at = @At("RETURN"))
    private void iris$endBlockEntity(TileEntity te, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        CTMUtils.clearCurrentCompact();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
        TesrAttribution.currentRenderable = null;
        TesrBlendScope.exit();
    }

    @Unique
    private static int iris$resolveBlockEntityId(TileEntity te) {
        return TesrProviderDispatch.resolveBlockEntityId(te);
    }
}
