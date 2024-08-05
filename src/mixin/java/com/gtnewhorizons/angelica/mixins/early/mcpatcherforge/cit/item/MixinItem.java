package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cit.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.prupe.mcpatcher.cit.CITUtils;

@Mixin(Item.class)
public abstract class MixinItem {

    @Shadow
    public abstract IIcon getIconFromDamage(int meta);

    @Redirect(
        method = "getIconIndex(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/util/IIcon;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/Item;getIconFromDamage(I)Lnet/minecraft/util/IIcon;"))
    private IIcon modifyGetIconIndex(Item item, int meta, ItemStack itemStack) {
        return CITUtils.getIcon(this.getIconFromDamage(meta), itemStack, 0);
    }
}
