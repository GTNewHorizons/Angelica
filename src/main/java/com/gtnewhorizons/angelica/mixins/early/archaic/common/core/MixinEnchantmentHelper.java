package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentHelper.class)
public class MixinEnchantmentHelper {
    @Shadow @Final private static EnchantmentHelper.ModifierDamage enchantmentModifierDamage;

    @Shadow @Final private static EnchantmentHelper.HurtIterator field_151388_d;

    @Shadow @Final private static EnchantmentHelper.DamageIterator field_151389_e;

    @Inject(method = "getEnchantmentModifierDamage", at = @At("RETURN"))
    private static void clearWorldReference1(ItemStack[] p_77508_0_, DamageSource p_77508_1_, CallbackInfoReturnable<Integer> cir) {
        enchantmentModifierDamage.source = null;
    }

    @Inject(method = "func_151384_a", at = @At("RETURN"))
    private static void clearWorldReference2(EntityLivingBase p_151384_0_, Entity p_151384_1_, CallbackInfo ci) {
        field_151388_d.field_151363_b = null;
        field_151388_d.field_151364_a = null;
    }

    @Inject(method = "func_151385_b", at = @At("RETURN"))
    private static void clearWorldReference3(EntityLivingBase p_151385_0_, Entity p_151385_1_, CallbackInfo ci) {
        field_151389_e.field_151366_a = null;
        field_151389_e.field_151365_b = null;
    }
}
