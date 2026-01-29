package com.gtnewhorizons.angelica.mixins.early.shaders;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.entity.RenderDragon;
import net.minecraft.entity.boss.EntityDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to set the entity ID to "end_crystal_beam" when rendering ender dragon healing beams.
 * This allows shaders to apply special effects to the healing beams separately from the dragon itself.
 */
@Mixin(RenderDragon.class)
public class MixinRenderDragon {
    @Unique
    private static final NamespacedId END_CRYSTAL_BEAM = new NamespacedId("minecraft", "end_crystal_beam");

    @Unique
    private int angelica$previousEntityId = -1;

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
}
