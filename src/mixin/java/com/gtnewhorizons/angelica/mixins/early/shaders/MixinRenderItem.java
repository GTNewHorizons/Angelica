 package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.ItemMaterialHelper;
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
        int id = ItemMaterialHelper.getMaterialId(itemStack);
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(id);
    }

    /**
     * Activate GLINT shader before rendering enchantment glint.
     * Injects at the first glDepthFunc inside the if(hasEffect) block.
     */
    @Inject(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 0),
        remap = false
    )
    private void iris$activateGlintShader(net.minecraft.entity.item.EntityItem entity, net.minecraft.util.IIcon icon, int count, float partialTicks, float r, float g, float b, int pass, CallbackInfo ci) {
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
    }

    /**
     * Deactivate GLINT shader after rendering enchantment glint.
     * Injects at the last glDepthFunc inside the if(hasEffect) block.
     */
    @Inject(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 1, shift = At.Shift.AFTER),
        remap = false
    )
    private void iris$deactivateGlintShader(net.minecraft.entity.item.EntityItem entity, net.minecraft.util.IIcon icon, int count, float partialTicks, float r, float g, float b, int pass, CallbackInfo ci) {
        GbufferPrograms.teardownSpecialRenderCondition();
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
