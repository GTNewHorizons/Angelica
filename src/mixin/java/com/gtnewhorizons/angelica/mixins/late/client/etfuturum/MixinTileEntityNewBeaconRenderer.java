package com.gtnewhorizons.angelica.mixins.late.client.etfuturum;

import ganymedes01.etfuturum.client.renderer.tileentity.TileEntityNewBeaconRenderer;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Et Futurum's replaces the regular vanilla beacon with its own.
 */
@Mixin(value = TileEntityNewBeaconRenderer.class, remap = false)
public class MixinTileEntityNewBeaconRenderer {

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", at = @At("HEAD"), remap = true)
    private void angelica$beginBeaconBeam(TileEntity te, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.BEACON_BEAM);
    }

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", at = @At("RETURN"), remap = true)
    private void angelica$endBeaconBeam(TileEntity te, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        GbufferPrograms.teardownSpecialRenderCondition();
    }
}
