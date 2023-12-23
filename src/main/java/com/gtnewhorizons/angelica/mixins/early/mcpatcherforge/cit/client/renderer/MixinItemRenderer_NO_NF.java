package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cit.client.renderer;

import com.prupe.mcpatcher.cit.CITUtils;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer_NO_NF {

    @Redirect(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasEffect(I)Z"),
        remap = false)
    private boolean modifyRenderItem3(ItemStack item, int pass, EntityLivingBase entity, ItemStack itemStack,
                                      int renderPass) {
        return !CITUtils.renderEnchantmentHeld(item, renderPass) && item.hasEffect(pass);
    }
}
