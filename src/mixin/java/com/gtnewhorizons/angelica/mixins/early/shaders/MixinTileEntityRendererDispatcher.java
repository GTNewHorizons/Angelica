package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.rendering.tesr.PassRebindGate;
import com.gtnewhorizons.angelica.rendering.tesr.TesrAttribution;
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

import java.util.Arrays;

@Mixin(TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcher {

    @Unique private static int[] iris$depths = new int[4];
    @Unique private static int iris$n;

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", at = @At("HEAD"))
    private void iris$setBlockEntityId(TileEntity te, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        if (iris$n == iris$depths.length) {
            iris$depths = Arrays.copyOf(iris$depths, iris$n * 2);
        }
        iris$depths[iris$n++] = GLStateManager.pushState(StateSet.BLEND);
        CTMUtils.clearCurrentCompact();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(iris$resolveBlockEntityId(te));
        TesrAttribution.currentRenderable = te != null ? te.getClass() : null;

        // Rebind Iris's pass in case the previous TESR changed it
        PassRebindGate.rebindIfDirty();
    }

    @Unique
    private static int iris$resolveBlockEntityId(TileEntity te) {
        return TesrProviderDispatch.resolveBlockEntityId(te);
    }

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", at = @At("RETURN"))
    private void iris$resetBlockEntityId(TileEntity te, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        CTMUtils.clearCurrentCompact();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
        TesrAttribution.currentRenderable = null;
        if (iris$n == 0) return;
        GLStateManager.popStateTo(iris$depths[--iris$n]);
    }
}
