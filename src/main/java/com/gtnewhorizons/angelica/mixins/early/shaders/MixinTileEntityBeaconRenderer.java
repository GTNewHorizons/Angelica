package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer;
import net.minecraft.tileentity.TileEntityBeacon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityBeaconRenderer.class)
public class MixinTileEntityBeaconRenderer {

    @Inject(method="Lnet/minecraft/client/renderer/tileentity/TileEntityBeaconRenderer;renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityBeacon;DDDF)V", at=@At("HEAD"))
    private void onRenderTileEntityAt(TileEntityBeacon beacon, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.BEACON_BEAM);
    }

    @Inject(method="Lnet/minecraft/client/renderer/tileentity/TileEntityBeaconRenderer;renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityBeacon;DDDF)V", at=@At("RETURN"))
    private void afterRenderTileEntityAt(TileEntityBeacon beacon, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        GbufferPrograms.teardownSpecialRenderCondition();
    }


}
