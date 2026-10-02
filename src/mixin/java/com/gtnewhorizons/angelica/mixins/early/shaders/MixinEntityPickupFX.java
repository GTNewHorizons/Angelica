package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.HandRenderer;
import net.minecraft.client.particle.EntityPickupFX;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
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

    @Surround(
        method = "renderParticle",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderManager;renderEntityWithPosYaw(Lnet/minecraft/entity/Entity;DDDFF)Z")
    )
    private void iris$wrapPickupRender(RenderManager renderManager, Entity entity) {
        final Boolean translucent = entity instanceof EntityItem item
            ? HandRenderer.INSTANCE.isItemTranslucent(item.getEntityItem())
            : null;

        @Surround.Carry Boolean previous = GbufferPrograms.beginTranslucencyDeclaration(translucent);
        GbufferPrograms.beginEntities();
    }

    @Surround.Finally
    private void iris$wrapPickupRenderEnd(@Surround.Carry Boolean previous) {
        GbufferPrograms.endEntities();
        GbufferPrograms.endTranslucencyDeclaration(previous);
    }
}
