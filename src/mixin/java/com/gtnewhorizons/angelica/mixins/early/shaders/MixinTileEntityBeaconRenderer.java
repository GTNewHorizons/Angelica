package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer;
import net.minecraft.tileentity.TileEntityBeacon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    /**
     * Whoever wrote this part of vanilla code sucks. Just change the alpha my guy.
     */
    @Redirect(
        method = "Lnet/minecraft/client/renderer/tileentity/TileEntityBeaconRenderer;renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityBeacon;DDDF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorRGBA(IIII)V", ordinal = 0))
    private void angelica$innerBeamColor(Tessellator tessellator, int r, int g, int b, int a) {
        angelica$beamColor(tessellator, r, g, b, 255);
    }

    @Redirect(
        method = "Lnet/minecraft/client/renderer/tileentity/TileEntityBeaconRenderer;renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityBeacon;DDDF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorRGBA(IIII)V", ordinal = 1))
    private void angelica$outerBeamColor(Tessellator tessellator, int r, int g, int b, int a) {
        angelica$beamColor(tessellator, r, g, b, a);
    }

    /**
     * Make beam fullbright.
     */
    @Unique
    private void angelica$beamColor(Tessellator tessellator, int r, int g, int b, int a) {
        tessellator.setColorRGBA(r, g, b, a);
        tessellator.setBrightness(0x00F000F0);
    }

}
