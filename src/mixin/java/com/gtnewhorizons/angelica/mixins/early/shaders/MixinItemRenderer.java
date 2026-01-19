package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.compat.mojang.InteractionHand;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.HandRenderer;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.ItemMaterialHelper;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to handle hand rendering and item ID tracking.
 */
@Mixin(ItemRenderer.class)
public class MixinItemRenderer {
    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"), cancellable = true)
    private void iris$handleFirstPersonRender(float partialTicks, CallbackInfo ci) {
        if (IrisApi.getInstance().isShaderPackInUse()) {
            boolean isHandTranslucent = HandRenderer.INSTANCE.isHandTranslucent(InteractionHand.MAIN_HAND);
            boolean isRenderingSolid = HandRenderer.INSTANCE.isRenderingSolid();

            if (isRenderingSolid == isHandTranslucent) {
                ci.cancel();
                return; // Don't set item ID since we're cancelling
            }
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        ItemStack itemStack = mc.thePlayer.getCurrentEquippedItem();

        if (itemStack == null || itemStack.getItem() == null) {
            CapturedRenderingState.INSTANCE.setCurrentRenderedItem(-1);
            return;
        }

        // Get material ID from item.properties or block.properties
        int id = ItemMaterialHelper.getMaterialId(itemStack);
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(id);
    }

    /**
     * Activate GLINT shader before rendering enchantment glint on held items.
     * Injects at the first glDepthFunc inside the if(hasEffect) block.
     */
    @Inject(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 0),
        remap = false
    )
    private void iris$activateGlintShaderHeldItem(net.minecraft.entity.EntityLivingBase entity, ItemStack itemStack, int renderPass, net.minecraftforge.client.IItemRenderer.ItemRenderType type, CallbackInfo ci) {
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
    }

    /**
     * Deactivate GLINT shader after rendering enchantment glint on held items.
     * Injects at the last glDepthFunc inside the if(hasEffect) block.
     */
    @Inject(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 1, shift = At.Shift.AFTER),
        remap = false
    )
    private void iris$deactivateGlintShaderHeldItem(net.minecraft.entity.EntityLivingBase entity, ItemStack itemStack, int renderPass, net.minecraftforge.client.IItemRenderer.ItemRenderType type, CallbackInfo ci) {
        GbufferPrograms.teardownSpecialRenderCondition();
    }

    /**
     * Reset the item ID after rendering first-person items.
     */
    @Inject(
        method = "renderItemInFirstPerson",
        at = @At("RETURN")
    )
    private void iris$resetHeldItemId(float partialTicks, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(-1);
    }
}
