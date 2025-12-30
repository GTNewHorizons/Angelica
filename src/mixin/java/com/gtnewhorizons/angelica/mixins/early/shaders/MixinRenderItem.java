 package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to set the currentRenderedItem ID when rendering dropped items (EntityItem).
 * This allows shaders to identify and apply different materials to items on the ground.
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
    private void iris$setItemIdBeforeRender(net.minecraft.entity.item.EntityItem entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        ItemStack itemStack = entity.getEntityItem();

        // Get material ID from item.properties or block.properties
        int id = net.coderbot.iris.uniforms.ItemMaterialHelper.getMaterialId(itemStack);
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(id);
    }

    /**
     * Reset the item ID after rendering dropped items.
     */
    @Inject(
        method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
        at = @At("RETURN")
    )
    private void iris$resetItemIdAfterRender(net.minecraft.entity.item.EntityItem entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
    }
}
