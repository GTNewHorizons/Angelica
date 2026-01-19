package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.ItemMaterialHelper;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.EntityLiving;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * Mixin to set the currentRenderedItem ID when rendering armor on entities.
 * This allows shaders to identify and apply different materials to armor pieces.
 */
@Mixin(RenderBiped.class)
public class MixinRenderBiped {

    /**
     * Set the item ID when rendering armor.
     * Wraps the setRenderPassModel call to inject our ID setting logic.
     */
    @WrapOperation(
        method = "shouldRenderPass(Lnet/minecraft/entity/EntityLiving;IF)I",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderBiped;setRenderPassModel(Lnet/minecraft/client/model/ModelBase;)V")
    )
    private void iris$setArmorItemId(RenderBiped instance, ModelBase model, Operation<Void> original, EntityLiving entity, int armorSlot, float partialTicks) {
        // Get the armor item stack for this slot
        ItemStack itemStack = entity.func_130225_q(3 - armorSlot);

        if (itemStack == null || itemStack.getItem() == null) {
            CapturedRenderingState.INSTANCE.setCurrentRenderedItem(-1);
            original.call(instance, model);
            return;
        }

        // Get material ID from item.properties or block.properties
        int id = ItemMaterialHelper.getMaterialId(itemStack);
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(id);

        // Call original method
        original.call(instance, model);
    }

    /**
     * Reset the item ID at the start of rendering each entity.
     * This ensures a clean slate and prevents ID bleed between entities.
     */
    @Inject(
        method = "doRender(Lnet/minecraft/entity/EntityLiving;DDDFF)V",
        at = @At("HEAD")
    )
    private void iris$resetAtStart(EntityLiving entity, double x, double y, double z, float entityYaw, float partialTicks, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(-1);
    }

    /**
     * Reset the item ID at the start of renderEquippedItems.
     * This prevents armor IDs from bleeding into held item rendering.
     */
    @Inject(
        method = "renderEquippedItems(Lnet/minecraft/entity/EntityLiving;F)V",
        at = @At("HEAD")
    )
    private void iris$resetInEquippedItems(EntityLiving entity, float partialTicks, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(-1);
    }

    /**
     * Set the item ID when rendering held items in third person.
     * Wraps renderItem calls to set the ID based on the held item.
     */
    @WrapOperation(
        method = "renderEquippedItems(Lnet/minecraft/entity/EntityLiving;F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;I)V")
    )
    private void iris$setHeldItemId(net.minecraft.client.renderer.ItemRenderer itemRenderer, net.minecraft.entity.EntityLivingBase entity, ItemStack itemStack, int renderPass, Operation<Void> original) {
        if (itemStack == null || itemStack.getItem() == null) {
            original.call(itemRenderer, entity, itemStack, renderPass);
            return;
        }

        // Get material ID from item.properties or block.properties
        int id = ItemMaterialHelper.getMaterialId(itemStack);
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(id);

        original.call(itemRenderer, entity, itemStack, renderPass);

        // Reset after rendering the held item
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(-1);
    }
}
