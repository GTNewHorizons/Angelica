package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.EntityIdHelper;
import net.coderbot.iris.uniforms.ItemMaterialHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin to handle material ID setting for entities rendered through renderEntityWithPosYaw.
 * This is needed for EntityPickupFX which renders entities during pickup animation.
 */
@Mixin(RenderManager.class)
public class MixinRenderManager {

    @WrapOperation(
        method = "renderEntityWithPosYaw",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V")
    )
    private void iris$wrapDoRender(net.minecraft.client.renderer.entity.Render render, Entity entity, double x, double y, double z, float entityYaw, float partialTicks, Operation<Void> original) {
        // Set appropriate material ID when entities are rendered through renderEntityWithPosYaw
        // (used by EntityPickupFX during pickup animation)
        boolean setItemId = false;
        boolean setEntityId = false;

        if (entity instanceof EntityItem entityItem) {
            ItemStack itemStack = entityItem.getEntityItem();
            int itemId = ItemMaterialHelper.getMaterialId(itemStack);
            CapturedRenderingState.INSTANCE.setCurrentRenderedItem(itemId);
            setItemId = true;
        } else {
            int entityId = EntityIdHelper.getEntityId(entity);
            if (entityId != -1) {
                CapturedRenderingState.INSTANCE.setCurrentEntity(entityId);
                setEntityId = true;
            }
        }

        try {
            original.call(render, entity, x, y, z, entityYaw, partialTicks);
        } finally {
            if (setItemId) {
                CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
            }
            if (setEntityId) {
                CapturedRenderingState.INSTANCE.setCurrentEntity(-1);
            }
        }
    }
}
