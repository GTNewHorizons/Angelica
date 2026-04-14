package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Fix enchantment glint not rendering on item edges by using the actual icon dimensions
 * instead of hardcoded 256x256. This ensures the edge geometry calculation matches the
 * main item render, allowing GL_EQUAL depth test to pass on all edges.
 */
@Mixin(ItemRenderer.class)
public class MixinHeldItemGlintEdges {

    /**
     * First glint pass (ordinal 1)
     */
    @ModifyArgs(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V", ordinal = 1)
    )
    private void iris$fixFirstGlintDimensions(Args args, EntityLivingBase entity, ItemStack stack, int pass, ItemRenderType type) {
        IIcon icon = entity.getItemIcon(stack, pass);
        if (icon != null) {
            args.set(5, icon.getIconWidth());
            args.set(6, icon.getIconHeight());
        }
    }

    /**
     * Second glint pass (ordinal 2)
     */
    @ModifyArgs(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V", ordinal = 2)
    )
    private void iris$fixSecondGlintDimensions(Args args, EntityLivingBase entity, ItemStack stack, int pass, ItemRenderType type) {
        IIcon icon = entity.getItemIcon(stack, pass);
        if (icon != null) {
            args.set(5, icon.getIconWidth());
            args.set(6, icon.getIconHeight());
        }
    }
}
