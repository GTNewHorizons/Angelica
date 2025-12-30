package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.compat.mojang.InteractionHand;
import net.coderbot.iris.pipeline.HandRenderer;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.api.v0.IrisApi;
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
    private void iris$skipTranslucentHands(float partialTicks, CallbackInfo ci) {
        if (IrisApi.getInstance().isShaderPackInUse()) {
            boolean isHandTranslucent = HandRenderer.INSTANCE.isHandTranslucent(InteractionHand.MAIN_HAND);
            boolean isRenderingSolid = HandRenderer.INSTANCE.isRenderingSolid();

            if (isRenderingSolid == isHandTranslucent) {
                ci.cancel();
            }
        }
    }

    /**
     * Set the item ID before rendering held items.
     * Hooks into renderItemInFirstPerson which is called for first-person rendering.
     * For third-person, the item rendering happens through entity rendering which we catch elsewhere.
     *
     * IMPORTANT: This must check the same hand rendering pass conditions as iris$skipTranslucentHands
     * to avoid setting the item ID when rendering will be cancelled.
     */
    @Inject(
        method = "renderItemInFirstPerson",
        at = @At("HEAD")
    )
    private void iris$setHeldItemId_HEAD(float partialTicks, CallbackInfo ci) {
        // Check if this rendering pass will be skipped
        if (IrisApi.getInstance().isShaderPackInUse()) {
            boolean isHandTranslucent = HandRenderer.INSTANCE.isHandTranslucent(InteractionHand.MAIN_HAND);
            boolean isRenderingSolid = HandRenderer.INSTANCE.isRenderingSolid();

            if (isRenderingSolid == isHandTranslucent) {
                return;
            }
        }

        // Get the currently held item from the Minecraft instance
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        ItemStack itemStack = mc.thePlayer.getCurrentEquippedItem();

        if (itemStack == null || itemStack.getItem() == null) {
            CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
            return;
        }

        // Get material ID from item.properties or block.properties
        int id = net.coderbot.iris.uniforms.ItemMaterialHelper.getMaterialId(itemStack);
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(id);
    }

    /**
     * Reset the item ID after rendering first-person items.
     */
    @Inject(
        method = "renderItemInFirstPerson",
        at = @At("RETURN")
    )
    private void iris$resetHeldItemId(float partialTicks, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
    }
}
