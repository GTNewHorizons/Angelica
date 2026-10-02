package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.rendering.tesr.PassRebindGate;
import com.gtnewhorizons.angelica.rendering.tesr.TesrAttribution;
import com.gtnewhorizons.angelica.rendering.RenderRecovery;
import com.gtnewhorizons.angelica.rendering.tesr.TesrProviderDispatch;
import com.prupe.mcpatcher.ctm.CTMUtils;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcher {

    @Surround(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V")
    private void iris$beginBlockEntity(TileEntity te, double x, double y, double z, float partialTicks) {
        RenderRecovery.throwIfCrashTestArmed();
        @Surround.Carry final int blendDepth = GLStateManager.pushState(StateSet.BLEND);
        CTMUtils.clearCurrentCompact();
        CapturedRenderingState.INSTANCE.pushCurrentBlockEntity();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(te == null ? null : te.getBlockType(), TesrProviderDispatch.blockMetadata(te));
        @Surround.Carry final Class<?> prevRenderable = TesrAttribution.currentRenderable;
        TesrAttribution.currentRenderable = te != null ? te.getClass() : null;
        PassRebindGate.rebindIfDirty();
    }

    @Surround.Finally
    private void iris$endBlockEntity(@Surround.Carry int blendDepth, @Surround.Carry Class<?> prevRenderable) {
        CTMUtils.clearCurrentCompact();
        CapturedRenderingState.INSTANCE.popCurrentBlockEntity();
        TesrAttribution.currentRenderable = prevRenderable;
        GLStateManager.popStateTo(blendDepth);
    }
}
