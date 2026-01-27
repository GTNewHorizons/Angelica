package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.EntityIdHelper;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin to handle material ID setting for entities rendered through renderEntityWithPosYaw and renderEntityStatic.
 * This is needed for EntityPickupFX which renders entities during pickup animation.
 */
@Mixin(RenderManager.class)
public class MixinRenderManager {

    @WrapOperation(
        method = {"renderEntityWithPosYaw", "renderEntityStatic"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V")
    )
    private void iris$wrapDoRender(Render render, Entity entity, double x, double y, double z, float entityYaw, float partialTicks, Operation<Void> original) {
        if (entity instanceof EntityItem entityItem) {
            ItemStack itemStack = entityItem.getEntityItem();
            ItemIdManager.setItemId(itemStack);
            original.call(render, entity, x, y, z, entityYaw, partialTicks);
            ItemIdManager.resetItemId();
        } else {
            int entityId = EntityIdHelper.getEntityId(entity);
            CapturedRenderingState.INSTANCE.setCurrentEntity(entityId);
            original.call(render, entity, x, y, z, entityYaw, partialTicks);
            CapturedRenderingState.INSTANCE.setCurrentEntity(-1);
        }
    }
}
