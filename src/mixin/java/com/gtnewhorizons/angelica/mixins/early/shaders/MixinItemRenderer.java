package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.compat.mojang.InteractionHand;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.HandRenderer;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to handle item rendering in first-person.
 */
@Mixin(ItemRenderer.class)
public class MixinItemRenderer {
    /**
     * Render held item in first person.
     */
    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"), cancellable = true)
    private void iris$setFirstPersonItemId(float partialTicks, CallbackInfo ci) {
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

        // Set item ID for first-person hand/item
        ItemIdManager.setItemId(itemStack);
    }

    /**
     * Reset the item ID after rendering first-person items.
     */
    @Inject(
        method = "renderItemInFirstPerson",
        at = @At("RETURN")
    )
    private void iris$resetFirstPersonItemId(float partialTicks, CallbackInfo ci) {
        ItemIdManager.resetItemId();
    }

    /**
     * Activate GLINT shader before rendering enchantment glint on held items (third person).
     */
    @Inject(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 0),
        remap = false
    )
    private void iris$glintStart(CallbackInfo ci) {
        ItemIdManager.resetItemId();
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
    }

    /**
     * Deactivate GLINT shader after rendering enchantment glint on held items (third person).
     */
    @Inject(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 1, shift = At.Shift.AFTER),
        remap = false
    )
    private void iris$glintEnd(CallbackInfo ci) {
        GbufferPrograms.teardownSpecialRenderCondition();
    }
}
