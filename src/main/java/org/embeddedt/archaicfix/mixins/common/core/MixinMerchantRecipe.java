package org.embeddedt.archaicfix.mixins.common.core;

import net.minecraft.item.ItemStack;
import net.minecraft.village.MerchantRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantRecipe.class)
public class MixinMerchantRecipe {
    @Shadow private ItemStack itemToBuy;

    @Shadow private ItemStack secondItemToBuy;

    @Inject(method = "<init>(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)V", at = @At("RETURN"))
    private void capItemAmounts(ItemStack p_i1941_1_, ItemStack p_i1941_2_, ItemStack p_i1941_3_, CallbackInfo ci) {
        if(this.itemToBuy != null)
            this.itemToBuy.stackSize = Math.min(this.itemToBuy.stackSize, this.itemToBuy.getMaxStackSize());
        if(this.secondItemToBuy != null)
            this.secondItemToBuy.stackSize = Math.min(this.secondItemToBuy.stackSize, this.secondItemToBuy.getMaxStackSize());
    }
}
