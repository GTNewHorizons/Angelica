package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.EntityIdHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin to handle entity ID setting for entities rendered through renderEntityWithPosYaw and renderEntityStatic.
 * Sets the entity ID for all entities. Item IDs for EntityItem are handled by their specific renderer mixins.
 */
@Mixin(RenderManager.class)
public class MixinRenderManager {

    @WrapOperation(
        method = "func_147939_a",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V")
    )
    private void iris$wrapDoRender(Render render, Entity entity, double x, double y, double z, float entityYaw, float partialTicks, Operation<Void> original) {
        int entityId = EntityIdHelper.getEntityId(entity);
        CapturedRenderingState.INSTANCE.setCurrentEntity(entityId);
        original.call(render, entity, x, y, z, entityYaw, partialTicks);
        CapturedRenderingState.INSTANCE.setCurrentEntity(-1);
    }
}
