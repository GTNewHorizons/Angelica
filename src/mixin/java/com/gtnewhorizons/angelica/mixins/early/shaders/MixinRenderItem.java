package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.shadercompat.ShaderGlint;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.item.EntityItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to set the currentRenderedItem ID when rendering dropped items.
 */
@Mixin(RenderItem.class)
public class MixinRenderItem {

    /**
     * Item ID and cutout draw state for dropped items.
     */
    @WrapMethod(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V")
    private void iris$droppedItemRender(EntityItem entity, double x, double y, double z, float entityYaw, float partialTicks, Operation<Void> original) {
        final int prevItemId = ItemIdManager.getItemId();
        final long prevCutout = GbufferPrograms.pushCutoutDefaults();

        ItemIdManager.setItemId(entity.getEntityItem());
        try {
            original.call(entity, x, y, z, entityYaw, partialTicks);
        } finally {
            ItemIdManager.setItemIdRaw(prevItemId);
            GbufferPrograms.popCutoutDefaults(prevCutout);
        }
    }

    /**
     * Activate GLINT shader before rendering enchantment glint on dropped items.
     */
    @Inject(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 0),
        remap = false
    )
    private void iris$glintStart(CallbackInfo ci) {
        ItemIdManager.resetItemId();
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
        ShaderGlint.beginGlint();
    }

    /**
     * Deactivate GLINT shader after rendering enchantment glint on dropped items.
     */
    @Inject(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 1, shift = At.Shift.AFTER),
        remap = false
    )
    private void iris$glintEnd(CallbackInfo ci) {
        GbufferPrograms.teardownSpecialRenderCondition();
        ShaderGlint.endGlint();
    }
}
