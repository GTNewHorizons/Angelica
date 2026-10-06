package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.api.EnderDragonBeams;
import net.minecraft.client.renderer.entity.RenderDragon;
import net.minecraft.entity.boss.EntityDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin allows devs to target the ender crystal beams and dragon death rays as separate entities.
 * Also sets the special condition "lightning" on the dragon's death beams.
 */
@Mixin(RenderDragon.class)
public abstract class MixinRenderDragon {
    @Unique
    private int angelica$depthPassReplay = 0;

    @Invoker("renderEquippedItems")
    protected abstract void angelica$invokeRenderEquippedItems(EntityDragon dragon, float partialTicks);

    @Surround(
        id = "crystalBeam",
        method = "doRender(Lnet/minecraft/entity/boss/EntityDragon;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I")
    )
    private void iris$beginCrystalBeam() {
        EnderDragonBeams.beginCrystalBeam();
    }

    @Surround.Finally("crystalBeam")
    private void iris$endCrystalBeam() {
        EnderDragonBeams.endCrystalBeam();
    }

    /**
     * We adapt the 2 pass structure that Modern Iris uses for the death beams.
     * Replay like this so mixins that target the vanilla code hopefully runs too.
     * <p>
     * Pass 1: Write the depth buffer.
     * Pass 2: Write color.
    */
    @Surround(id = "deathBeams", method = "renderEquippedItems(Lnet/minecraft/entity/boss/EntityDragon;F)V")
    private void angelica$beginDeathBeamsLightningBuffer(EntityDragon dragon, float partialTicks) {
        @Surround.Carry
        final boolean deathBeams = angelica$depthPassReplay == 0 && dragon.deathTicks > 0;
        if (deathBeams) {
            EnderDragonBeams.beginDeathRays();

            angelica$depthPassReplay++;
            GLStateManager.glColorMask(false, false, false, false);
            try {
                angelica$invokeRenderEquippedItems(dragon, partialTicks);
            } catch (Throwable t) {
                angelica$endDeathBeamsLighting(true);
                throw t;
            } finally {
                GLStateManager.glColorMask(true, true, true, true);
                angelica$depthPassReplay--;
            }
        }
    }

    // Don't render items twice
    @Surround(
        id = "skipSuper",
        method = "renderEquippedItems(Lnet/minecraft/entity/boss/EntityDragon;F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderLiving;renderEquippedItems(Lnet/minecraft/entity/EntityLivingBase;F)V")
    )
    private void angelica$skipSuperDuringReplay() {
        @Surround.Skip
        boolean skip = angelica$depthPassReplay != 0;
    }

    // No-op the depth buffer being set to false on first pass
    @Surround(
        id = "keepDepthMask",
        method = "renderEquippedItems(Lnet/minecraft/entity/boss/EntityDragon;F)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthMask(Z)V", ordinal = 0, remap = false)
    )
    private void angelica$keepDepthMaskDuringReplay() {
        @Surround.Skip
        boolean skip = angelica$depthPassReplay != 0;
    }

    @Surround.Finally("deathBeams")
    private void angelica$endDeathBeamsLighting(@Surround.Carry boolean deathBeams) {
        if (deathBeams) EnderDragonBeams.endDeathRays();
    }
}
