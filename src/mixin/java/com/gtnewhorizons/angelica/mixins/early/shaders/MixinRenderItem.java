package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.uniforms.ItemIdManager;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to set the currentRenderedItem ID when rendering dropped items.
 */
@Mixin(RenderItem.class)
public class MixinRenderItem {

    /**
     * Set the item ID before rendering dropped items.
     * Checks both item.properties and block.properties.
     */
    @Inject(
        method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
        at = @At("HEAD")
    )
    private void iris$setItemIdBeforeRender(EntityItem entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        ItemStack itemStack = entity.getEntityItem();
        ItemIdManager.setItemId(itemStack);
    }

    /**
     * Reset the item ID after rendering dropped items.
     */
    @Inject(
        method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
        at = @At("RETURN")
    )
    private void iris$resetItemIdAfterRender(EntityItem entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        ItemIdManager.resetItemId();
    }
}
