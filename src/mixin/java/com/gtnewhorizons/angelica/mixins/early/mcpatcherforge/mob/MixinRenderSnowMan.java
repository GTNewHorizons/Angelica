package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.mob;

import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderSnowMan;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.prupe.mcpatcher.mob.MobOverlay;

@Mixin(RenderSnowMan.class)
public abstract class MixinRenderSnowMan {

    @WrapWithCondition(
        method = "renderEquippedItems(Lnet/minecraft/entity/monster/EntitySnowman;F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;I)V"))
    private boolean modifyRenderEquippedItems(ItemRenderer renderer, EntityLivingBase entity, ItemStack itemStack,
        int i) {
        return !MobOverlay.renderSnowmanOverlay(entity);
    }
}
