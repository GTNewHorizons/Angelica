package com.gtnewhorizons.angelica.mixins.early.angelica.tesr;

import com.gtnewhorizons.angelica.rendering.tesr.BeaconBeamMeshes;
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer;
import net.minecraft.tileentity.TileEntityBeacon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityBeaconRenderer.class)
public class MixinTileEntityBeaconRenderer {

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityBeacon;DDDF)V", at = @At("HEAD"), cancellable = true)
    private void angelica$batchBeam(TileEntityBeacon beacon, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        if (BeaconBeamMeshes.render(beacon, x, y, z, partialTicks)) {
            ci.cancel();
        }
    }
}
