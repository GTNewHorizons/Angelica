package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.layer.GbufferPrograms;
import net.minecraft.client.particle.EntityPickupFX;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin to handle EntityPickupFX rendering items during pickup animation.
 * EntityPickupFX renders particles during the PARTICLES phase, but it renders
 * the actual EntityItem using RenderManager.renderEntityWithPosYaw. We need
 * to switch to ENTITIES phase during this render to apply proper materials.
 */
@Mixin(EntityPickupFX.class)
public class MixinEntityPickupFX {

    @WrapOperation(
        method = "renderParticle",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderManager;renderEntityWithPosYaw(Lnet/minecraft/entity/Entity;DDDFF)Z")
    )
    private boolean iris$wrapPickupRender(RenderManager renderManager, Entity entity, double x, double y, double z, float yaw, float partialTicks, Operation<Boolean> original) {
        GbufferPrograms.beginEntities();
        try {
            return original.call(renderManager, entity, x, y, z, yaw, partialTicks);
        } finally {
            GbufferPrograms.endEntities();
        }
    }
}
