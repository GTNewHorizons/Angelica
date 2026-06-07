package com.gtnewhorizons.angelica.mixins.late.chisel;

import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chisel ships its own beacon beam renderer, we yoink a lot of the code from
 * {@link com.gtnewhorizons.angelica.mixins.early.shaders.MixinTileEntityBeaconRenderer} to apply the same fixes here.
 */
@Pseudo
@Mixin(targets = { "team.chisel.client.render.tile.RenderCarvableBeacon" }, remap = false)
public class MixinRenderCarvableBeacon {

    @Inject(method = "renderBeam(FLnet/minecraft/world/World;DDDIF)V", at = @At("HEAD"))
    private void angelica$beginBeaconBeam(float f1, World world, double x, double y, double z, int meta, float partialTicks, CallbackInfo ci) {
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.BEACON_BEAM);
    }

    @Inject(method = "renderBeam(FLnet/minecraft/world/World;DDDIF)V", at = @At("RETURN"))
    private void angelica$endBeaconBeam(float f1, World world, double x, double y, double z, int meta, float partialTicks, CallbackInfo ci) {
        GbufferPrograms.teardownSpecialRenderCondition();
    }

    @Redirect(
        method = "renderBeam(FLnet/minecraft/world/World;DDDIF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorRGBA(IIII)V", ordinal = 0, remap = true))
    private void angelica$innerBeamColor(Tessellator tessellator, int r, int g, int b, int a) {
        angelica$beamColor(tessellator, r, g, b, 255);
    }

    @Redirect(
        method = "renderBeam(FLnet/minecraft/world/World;DDDIF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorRGBA(IIII)V", ordinal = 1, remap = true))
    private void angelica$outerBeamColor(Tessellator tessellator, int r, int g, int b, int a) {
        angelica$beamColor(tessellator, r, g, b, a);
    }

    @Unique
    private void angelica$beamColor(Tessellator tessellator, int r, int g, int b, int a) {
        tessellator.setColorRGBA(r, g, b, a);
        tessellator.setBrightness(0x00F000F0);
    }
}
