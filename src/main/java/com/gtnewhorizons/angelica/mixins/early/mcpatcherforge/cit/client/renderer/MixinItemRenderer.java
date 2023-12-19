package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cit.client.renderer;

import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.prupe.mcpatcher.cit.CITUtils;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer {

    @Redirect(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/EntityLivingBase;getItemIcon(Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/util/IIcon;"))
    private IIcon modifyRenderItem1(EntityLivingBase instance, ItemStack item, int renderPass, EntityLivingBase entity,
        ItemStack item2, int renderPass1) {
        return CITUtils.getIcon(entity.getItemIcon(item2, renderPass1), item2, renderPass1);
    }

    //TODO put com.gtnewhorizons.angelica.mixins.early.notfine.glint.notFine$toggleGlint change back to this mixins by ModifyReturnValue here as well as in NotFine
    /*
    @Redirect(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasEffect(I)Z"),
        remap = false)
    private boolean modifyRenderItem3(ItemStack item, int pass, EntityLivingBase entity, ItemStack itemStack,
        int renderPass) {
        return !CITUtils.renderEnchantmentHeld(item, renderPass) && item.hasEffect(pass);
    }

     */
}
