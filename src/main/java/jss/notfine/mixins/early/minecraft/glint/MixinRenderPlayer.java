package jss.notfine.mixins.early.minecraft.glint;

import jss.notfine.core.Settings;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderPlayer.class)
public abstract class MixinRenderPlayer {

    @Redirect(
        method = "shouldRenderPass(Lnet/minecraft/client/entity/AbstractClientPlayer;IF)I",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;isItemEnchanted()Z"
        )
    )
    private boolean notFine$toggleGlint(ItemStack stack) {
        return Settings.MODE_GLINT_WORLD.isValueBase() && stack.isItemEnchanted();
    }

}
