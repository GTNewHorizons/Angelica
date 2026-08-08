package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.backhand.BackhandReflectionCompat;
import com.gtnewhorizons.angelica.compat.mojang.InteractionHand;
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

@Mixin(ItemRenderer.class)
public class MixinItemRendererBackhand {

    @Shadow private ItemStack itemToRender;

    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"), cancellable = true)
    private void iris$skipTranslucentHands(float partialTicks, CallbackInfo ci) {
        if (IrisApi.getInstance().isShaderPackInUse()
            && HandRenderer.INSTANCE.isRenderingSolid() == HandRenderer.INSTANCE.isItemTranslucent(this.itemToRender)) {
            ci.cancel();
            BackhandReflectionCompat.renderOffhand(partialTicks);
            return;
        }
        ItemIdManager.setItemId(this.itemToRender);
    }

    @Inject(method = "renderItemInFirstPerson", at = @At("RETURN"), order = 800)
    private void iris$resetFirstPersonItemId(float partialTicks, CallbackInfo ci) {
        ItemIdManager.resetItemId();
    }

    // change injection to before Backhand's inject
    @Inject(method = "renderItemInFirstPerson", at = @At("RETURN"), cancellable = true, order = 900)
    private void iris$skipTranslucentHandsBackhand(float partialTicks, CallbackInfo ci) {
        if (ModStatus.isBackhandLoaded && IrisApi.getInstance().isShaderPackInUse()
            && HandRenderer.INSTANCE.isRenderingSolid() == HandRenderer.INSTANCE.isHandTranslucent(InteractionHand.OFF_HAND)) {
            ci.cancel();
        }
    }
}
