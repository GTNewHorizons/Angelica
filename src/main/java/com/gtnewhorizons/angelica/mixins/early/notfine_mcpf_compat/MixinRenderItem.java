package com.gtnewhorizons.angelica.mixins.early.notfine_mcpf_compat;

import com.prupe.mcpatcher.cit.CITUtils;
import jss.notfine.core.Settings;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderItem.class)
public class MixinRenderItem {

    @Redirect(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;hasEffect(I)Z"
        ),
        remap = false
    )
    private boolean notFine$toggleGlint(ItemStack stack, int pass) {
        return stack.hasEffect(pass) && !CITUtils.renderEnchantmentDropped(stack) && (boolean) Settings.MODE_GLINT_WORLD.option.getStore();
    }

}
