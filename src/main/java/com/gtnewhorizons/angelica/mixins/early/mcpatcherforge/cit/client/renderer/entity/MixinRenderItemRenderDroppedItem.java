package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cit.client.renderer.entity;

import com.prupe.mcpatcher.cit.CITUtils;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderItem.class)
public class MixinRenderItemRenderDroppedItem {

    @Redirect(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasEffect(I)Z", remap = false),
        remap = false)
    private boolean modifyRenderDroppedItem(ItemStack instance, int pass) {
        return !CITUtils.renderEnchantmentDropped(instance) && instance.hasEffect(pass);
    }
}
