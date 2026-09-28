package com.gtnewhorizons.angelica.mixins.early.notfine.glint;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import jss.notfine.core.Settings;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ItemRenderer.class)
public abstract class MixinItemRenderer {

    @ModifyExpressionValue(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;hasEffect(I)Z"
        ),
        remap = false
    )
    private boolean notFine$toggleGlint(boolean hasEffect) {
        return hasEffect && (boolean) Settings.MODE_GLINT_WORLD.option.getStore();
    }

}
