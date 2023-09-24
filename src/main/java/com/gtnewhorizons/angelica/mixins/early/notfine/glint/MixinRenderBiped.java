package com.gtnewhorizons.angelica.mixins.early.notfine.glint;

import jss.notfine.core.Settings;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderBiped.class)
public abstract class MixinRenderBiped {

    @Redirect(
        method = "shouldRenderPass(Lnet/minecraft/entity/EntityLiving;IF)I",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;isItemEnchanted()Z"
        )
    )
    private boolean notFine$toggleGlint(ItemStack stack) {
        return Settings.MODE_GLINT_WORLD.isValueBase() && stack.isItemEnchanted();
    }

}
