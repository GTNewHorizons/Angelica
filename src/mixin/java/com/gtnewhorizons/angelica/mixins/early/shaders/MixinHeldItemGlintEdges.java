package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.util.IIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(ItemRenderer.class)
public class MixinHeldItemGlintEdges {

    @ModifyArg(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V"),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V", ordinal = 1)),
        index = 5
    )
    private int iris$glintWidth(int width, @Local IIcon icon) {
        return icon.getIconWidth();
    }

    @ModifyArg(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V"),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V", ordinal = 1)),
        index = 6
    )
    private int iris$glintHeight(int height, @Local IIcon icon) {
        return icon.getIconHeight();
    }
}
