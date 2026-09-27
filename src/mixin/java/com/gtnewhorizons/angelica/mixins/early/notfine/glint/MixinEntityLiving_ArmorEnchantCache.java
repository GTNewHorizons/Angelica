package com.gtnewhorizons.angelica.mixins.early.notfine.glint;

import com.gtnewhorizons.angelica.mixins.interfaces.ArmorEnchantCache;
import net.minecraft.entity.EntityLiving;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityLiving.class)
public abstract class MixinEntityLiving_ArmorEnchantCache implements ArmorEnchantCache {
    @Unique private ItemStack[] angelica$checkedArmor;
    @Unique private int angelica$enchantedSlots;

    @Override
    public boolean angelica$isArmorEnchanted(int slot, ItemStack stack) {
        if (slot < 0 || slot >= 4) return stack.isItemEnchanted();
        ItemStack[] checked = angelica$checkedArmor;
        if (checked == null) checked = angelica$checkedArmor = new ItemStack[4];
        final int bit = 1 << slot;
        if (checked[slot] != stack) {
            checked[slot] = stack;
            angelica$enchantedSlots = stack.isItemEnchanted() ? angelica$enchantedSlots | bit : angelica$enchantedSlots & ~bit;
        }
        return (angelica$enchantedSlots & bit) != 0;
    }
}
