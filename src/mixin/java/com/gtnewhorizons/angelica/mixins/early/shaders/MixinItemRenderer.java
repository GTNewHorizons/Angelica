package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.pipeline.HandRenderer;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to handle item rendering in first-person, and to set the currentRenderedItem for every
 * item rendered on an entity.
 */
@Mixin(ItemRenderer.class)
public class MixinItemRenderer {

    @Shadow private ItemStack itemToRender;

    /**
     * Render held item in first person.
     */
    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"), cancellable = true)
    private void iris$setFirstPersonItemId(float partialTicks, CallbackInfo ci) {
        if (IrisApi.getInstance().isShaderPackInUse()
            && HandRenderer.INSTANCE.isRenderingSolid() == HandRenderer.INSTANCE.isItemTranslucent(this.itemToRender)) {
            ci.cancel();
            return; // Don't set item ID since we're cancelling
        }

        // Wait for hand to lower before setting ID
        ItemIdManager.setItemId(this.itemToRender);
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

}
