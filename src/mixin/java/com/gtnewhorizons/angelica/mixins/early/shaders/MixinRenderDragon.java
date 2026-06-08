package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.entity.RenderDragon;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin allows devs to target the ender crystal beams and dragon death rays as separate entities.
 * Also sets the special condition "lightning" on the dragon's death beams.
 */
@Mixin(RenderDragon.class)
public abstract class MixinRenderDragon {
    @Unique
    private static final NamespacedId END_CRYSTAL_BEAM = new NamespacedId("minecraft", "end_crystal_beam");

    @Unique
    private static final NamespacedId DRAGON_DEATH_RAY = new NamespacedId("minecraft", "dragon_death_rays");

    @Unique
    private int angelica$previousEntityId = -1;

    @Unique
    private int angelica$previousDeathRayEntityId = -1;

    @Unique
    private boolean angelica$deathBeamsActive = false;

    @Unique
    private int angelica$depthPassReplay = 0;

    @Invoker("renderEquippedItems")
    protected abstract void angelica$invokeRenderEquippedItems(EntityDragon dragon, float partialTicks);

    @Inject(
        method = "doRender(Lnet/minecraft/entity/boss/EntityDragon;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPushMatrix()V", ordinal = 0, shift = At.Shift.AFTER, remap = false)
    )
    private void iris$setBeamEntityId(EntityDragon dragon, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        // Only set the ID if the dragon is being healed by a crystal
        if (dragon.healingEnderCrystal != null) {
            Object2IntFunction<NamespacedId> entityIdMap = BlockRenderingSettings.INSTANCE.getEntityIds();
            if (entityIdMap != null) {
                // Save the current entity ID
                angelica$previousEntityId = CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();

                int beamId = entityIdMap.applyAsInt(END_CRYSTAL_BEAM);
                CapturedRenderingState.INSTANCE.setCurrentEntity(beamId);
            }
        }
    }

    @Inject(
        method = "doRender(Lnet/minecraft/entity/boss/EntityDragon;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPopMatrix()V", ordinal = 0, shift = At.Shift.BEFORE, remap = false)
    )
    private void iris$restoreEntityId(EntityDragon dragon, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        if (angelica$previousEntityId != -1) {
            CapturedRenderingState.INSTANCE.setCurrentEntity(angelica$previousEntityId);
            angelica$previousEntityId = -1;
        }
    }

    /**
     * We adapt the 2 pass structure that Modern Iris uses for the death beams.
     * Replay like this so mixins that target the vanilla code hopefully runs too.
     * <p>
     * Pass 1: Write the depth buffer.
     * Pass 2: Write color.
    */
    @Inject(method = "renderEquippedItems(Lnet/minecraft/entity/boss/EntityDragon;F)V", at = @At("HEAD"))
    private void angelica$beginDeathBeamsLightningBuffer(EntityDragon dragon, float partialTicks, CallbackInfo ci) {
        if (angelica$depthPassReplay > 0) return;
        if (dragon.deathTicks > 0) {
            GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.LIGHTNING);
            angelica$deathBeamsActive = true;

            Object2IntFunction<NamespacedId> entityIdMap = BlockRenderingSettings.INSTANCE.getEntityIds();
            if (entityIdMap != null) {
                angelica$previousDeathRayEntityId = CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
                int deathRayId = entityIdMap.applyAsInt(DRAGON_DEATH_RAY);
                CapturedRenderingState.INSTANCE.setCurrentEntity(deathRayId);
            }

            angelica$depthPassReplay++;
            GLStateManager.glColorMask(false, false, false, false);
            angelica$invokeRenderEquippedItems(dragon, partialTicks);
            GLStateManager.glColorMask(true, true, true, true);
            angelica$depthPassReplay--;
        }
    }

    // Don't render items twice
    @WrapOperation(
        method = "renderEquippedItems(Lnet/minecraft/entity/boss/EntityDragon;F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderLiving;renderEquippedItems(Lnet/minecraft/entity/EntityLivingBase;F)V")
    )
    private void angelica$skipSuperDuringReplay(RenderDragon self, EntityLivingBase entity, float partialTicks, Operation<Void> original) {
        if (angelica$depthPassReplay == 0) {
            original.call(self, entity, partialTicks);
        }
    }

    // No-op the depth buffer being set to false on first pass
    @WrapOperation(
        method = "renderEquippedItems(Lnet/minecraft/entity/boss/EntityDragon;F)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthMask(Z)V", ordinal = 0, remap = false)
    )
    private void angelica$keepDepthMaskDuringReplay(boolean flag, Operation<Void> original) {
        if (angelica$depthPassReplay == 0) {
            original.call(flag);
        }
    }

    // End replay
    @Inject(method = "renderEquippedItems(Lnet/minecraft/entity/boss/EntityDragon;F)V", at = @At("RETURN"))
    private void angelica$endDeathBeamsLighting(EntityDragon dragon, float partialTicks, CallbackInfo ci) {
        if (angelica$depthPassReplay > 0) return;
        if (angelica$deathBeamsActive) {
            if (angelica$previousDeathRayEntityId != -1) {
                CapturedRenderingState.INSTANCE.setCurrentEntity(angelica$previousDeathRayEntityId);
                angelica$previousDeathRayEntityId = -1;
            }
            GbufferPrograms.teardownSpecialRenderCondition();
            angelica$deathBeamsActive = false;
        }
    }
}
