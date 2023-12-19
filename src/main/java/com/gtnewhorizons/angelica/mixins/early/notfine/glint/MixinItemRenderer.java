package com.gtnewhorizons.angelica.mixins.early.notfine.glint;

import com.prupe.mcpatcher.cit.CITUtils;
import jss.notfine.core.Settings;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ItemRenderer.class)
public abstract class MixinItemRenderer {

    @Redirect(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;hasEffect(I)Z"
        ),
        remap = false
    )
    private boolean notFine$toggleGlint(ItemStack stack, int pass) {
        //TODO: ensure this goes back to separate mixins
        return (boolean)Settings.MODE_GLINT_WORLD.option.getStore() && stack.hasEffect(pass) && !CITUtils.renderEnchantmentHeld(stack, pass);
    }

}
