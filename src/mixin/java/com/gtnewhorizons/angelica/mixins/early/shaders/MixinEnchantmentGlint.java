package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Centralized mixin to handle enchantment glint rendering across all contexts.
 * Activates the GLINT shader program before rendering enchantment effects and deactivates after.
 *
 * Handles three contexts:
 * 1. Held items (first and third person) via ItemRenderer.renderItem()
 * 2. Dropped items on ground via RenderItem.renderDroppedItem()
 * 3. Armor on entities via RendererLivingEntity.doRender()
 */
@Mixin(value = {ItemRenderer.class, RenderItem.class, RendererLivingEntity.class})
public class MixinEnchantmentGlint {

    /**
     * Activate GLINT shader before rendering enchantment glint on held items.
     * Injects at the first glDepthFunc inside the "if(hasEffect)" block.
     * Handles enchanted held items in both first and third person.
     */
    @Inject(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 0, shift = At.Shift.BEFORE),
        remap = false
    )
    private void iris$heldItemGlintStart(EntityLivingBase entity, ItemStack itemStack, int renderPass, IItemRenderer.ItemRenderType type, CallbackInfo ci) {
        ItemIdManager.resetItemId();
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
    }

    /**
     * Deactivate GLINT shader after rendering enchantment glint on held items.
     * Injects at the last glDepthFunc inside the "if(hasEffect)" block.
     */
    @Inject(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 1, shift = At.Shift.AFTER),
        remap = false
    )
    private void iris$heldItemGlintEnd(EntityLivingBase entity, ItemStack itemStack, int renderPass, IItemRenderer.ItemRenderType type, CallbackInfo ci) {
        GbufferPrograms.teardownSpecialRenderCondition();
    }

    /**
     * Activate GLINT shader before rendering enchantment glint on dropped items.
     * Injects at the first glDepthFunc inside the "if(hasEffect)" block.
     * Handles enchanted items dropped on the ground.
     */
    @Inject(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 0, shift = At.Shift.BEFORE),
        remap = false
    )
    private void iris$droppedItemGlintStart(EntityItem entity, IIcon icon, int count, float partialTicks, float r, float g, float b, int pass, CallbackInfo ci) {
        ItemIdManager.resetItemId();
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
    }

    /**
     * Deactivate GLINT shader after rendering enchantment glint on dropped items.
     * Injects at the last glDepthFunc inside the "if(hasEffect)" block.
     */
    @Inject(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 1, shift = At.Shift.AFTER),
        remap = false
    )
    private void iris$droppedItemGlintEnd(EntityItem entity, IIcon icon, int count, float partialTicks, float r, float g, float b, int pass, CallbackInfo ci) {
        GbufferPrograms.teardownSpecialRenderCondition();
    }

    /**
     * Activate GLINT shader before rendering enchantment glint on armor.
     * Injects at the first glDepthFunc inside the "if(hasEffect)" block.
     * Handles enchanted armor worn by entities.
     */
    @Inject(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 0, shift = At.Shift.BEFORE),
        remap = false
    )
    private void iris$armorGlintStart(EntityLivingBase entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        ItemIdManager.resetItemId();
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
    }

    /**
     * Deactivate GLINT shader after rendering enchantment glint on armor.
     * Injects at the last glDepthFunc inside the "if(hasEffect)" block.
     */
    @Inject(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 1, shift = At.Shift.AFTER),
        remap = false
    )
    private void iris$armorGlintEnd(EntityLivingBase entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        GbufferPrograms.teardownSpecialRenderCondition();
    }
}
