package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityHorse.class)
public interface AccessorEntityHorse {

    @Invoker("getHorseArmorIndex")
    int invokeGetHorseArmorIndex(ItemStack stack);
}
