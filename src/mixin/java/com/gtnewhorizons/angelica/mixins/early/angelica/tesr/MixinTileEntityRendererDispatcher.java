package com.gtnewhorizons.angelica.mixins.early.angelica.tesr;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.IBatchEligibility;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import com.gtnewhorizons.angelica.rendering.tesr.TesrProviderDispatch;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcher {

    @Redirect(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V"))
    private void angelica$dispatchTesrProvider(TileEntitySpecialRenderer renderer, TileEntity te, double x, double y, double z, float partialTicks) {
        if (AngelicaConfig.enableTESRProviderDispatch && TesrProviderDispatch.tryRender(renderer, te, x, y, z)) return;
        if (!AngelicaConfig.enableEntityBatching) {
            renderer.renderTileEntityAt(te, x, y, z, partialTicks);
            return;
        }

        final IBatchEligibility holder = (IBatchEligibility) renderer;
        final byte state = holder.angelica$batchState();
        BatchEligibility.begin(state, GLStateManager.drawCalls);
        try {
            renderer.renderTileEntityAt(te, x, y, z, partialTicks);
        } finally {
            final byte next = BatchEligibility.end(state, GLStateManager.drawCalls);
            if (next != state) {
                holder.angelica$setBatchState(next);
                BatchEligibility.onStateChange(renderer, next);
            }
        }
    }
}
