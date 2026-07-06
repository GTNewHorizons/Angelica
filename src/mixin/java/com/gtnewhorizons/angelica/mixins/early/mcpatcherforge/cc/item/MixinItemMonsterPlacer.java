package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.item;

import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.prupe.mcpatcher.cc.ColorizeItem;

@Mixin(ItemMonsterPlacer.class)
public abstract class MixinItemMonsterPlacer {

    @Inject(method = "getColorFromItemStack(Lnet/minecraft/item/ItemStack;I)I", at = @At("RETURN"), cancellable = true)
    private void modifyGetColorFromItemStack(ItemStack itemStack, int spots, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ColorizeItem.colorizeSpawnerEgg(cir.getReturnValue(), itemStack.getItemDamage(), spots));
    }
}
