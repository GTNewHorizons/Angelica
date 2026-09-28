package com.gtnewhorizons.angelica.mixins.late.client.thaumcraft;

import com.gtnewhorizons.angelica.compat.thaumcraft.ThaumometerScreen;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.coderbot.iris.pipeline.HandRenderer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumcraft.client.renderers.item.ItemThaumometerRenderer;

@Mixin(value = ItemThaumometerRenderer.class, remap = false)
public abstract class MixinItemThaumometerRenderer {
    @Unique private ThaumometerScreen.Capture angelica$screen;

    @Inject(method = "renderItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraftforge/client/model/IModelCustom;renderAll()V", shift = At.Shift.AFTER))
    private void angelica$captureScreen(ItemRenderType type, ItemStack item, Object[] data, CallbackInfo ci) {
        if (type == ItemRenderType.EQUIPPED_FIRST_PERSON && HandRenderer.INSTANCE.isActive()) {
            angelica$screen = ThaumometerScreen.begin();
        }
    }

    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPopMatrix()V", ordinal = 6))
    private void angelica$finishScreen(ItemRenderType type, ItemStack item, Object[] data, CallbackInfo ci) {
        if (angelica$screen != null) {
            ThaumometerScreen.finish(angelica$screen);
            angelica$screen = null;
        }
    }

    @WrapMethod(method = "renderItem")
    private void angelica$cleanupCapture(ItemRenderType type, ItemStack item, Object[] data, Operation<Void> original) {
        try {
            original.call(type, item, data);
        } finally {
            if (angelica$screen != null) {
                ThaumometerScreen.abort(angelica$screen);
                angelica$screen = null;
            }
        }
    }
}
