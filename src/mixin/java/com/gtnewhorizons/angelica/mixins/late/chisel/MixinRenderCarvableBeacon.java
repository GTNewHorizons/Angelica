package com.gtnewhorizons.angelica.mixins.late.chisel;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Chisel ships its own beacon beam renderer, we yoink a lot of the code from
 * {@link com.gtnewhorizons.angelica.mixins.early.shaders.MixinTileEntityBeaconRenderer} to apply the same fixes here.
 */
@Pseudo
@Mixin(targets = { "team.chisel.client.render.tile.RenderCarvableBeacon" }, remap = false)
public class MixinRenderCarvableBeacon {

    @WrapMethod(method = "renderBeam(FLnet/minecraft/world/World;DDDIF)V", require = 1)
    private void angelica$wrapBeaconBeam(float f1, World world, double x, double y, double z, int meta, float partialTicks, Operation<Void> original) {
        // Chisel's beam leaves GL_CULL_FACE disabled behind it
        final int stateDepth = GLStateManager.pushState(StateSet.CULL);
        try {
            GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.BEACON_BEAM);
            original.call(f1, world, x, y, z, meta, partialTicks);
        } finally {
            GbufferPrograms.teardownSpecialRenderCondition();
            GLStateManager.popStateTo(stateDepth);
        }
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
