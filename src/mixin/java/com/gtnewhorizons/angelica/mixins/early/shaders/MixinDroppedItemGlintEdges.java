package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.IIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(RenderItem.class)
public class MixinDroppedItemGlintEdges {

    /**
     * Fix enchantment glint not rendering on item edges by using the actual icon dimensions
     * instead of hardcoded 255x255. This ensures the edge geometry calculation matches the
     * main item render, allowing GL_EQUAL depth test to pass on all edges.

     * First glint pass (ordinal 1)
     */
    @ModifyArgs(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V", ordinal = 1)
    )
    private void iris$fixFirstGlintDimensions(Args args, EntityItem entity, IIcon icon, int count, float partialTicks, float r, float g, float b, int pass) {
        args.set(5, icon.getIconWidth());
        args.set(6, icon.getIconHeight());
    }

    /**
     * Second glint pass (ordinal 2)
     */
    @ModifyArgs(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V", ordinal = 2)
    )
    private void iris$fixSecondGlintDimensions(Args args, EntityItem entity, IIcon icon, int count, float partialTicks, float r, float g, float b, int pass) {
        args.set(5, icon.getIconWidth());
        args.set(6, icon.getIconHeight());
    }
}
