package com.gtnewhorizons.angelica.mixins.early.angelica.animation;

import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftForgeClient.class)
public class MixinMinecraftForgeClient {

    /**
     * @author laetansky We can just mark any item texture that gets rendered for an update
     */
    @Inject(method = "getItemRenderer", at = @At("HEAD"), remap = false)
    private static void hodgepodge$beforeRenderItem(ItemStack itemStack, IItemRenderer.ItemRenderType type,
            CallbackInfoReturnable<IItemRenderer> cir) {
        final Item item = itemStack.getItem();
        if (item.requiresMultipleRenderPasses()) {
            for (int i = 0; i < item.getRenderPasses(itemStack.getItemDamage()); i++) {
                IIcon icon = item.getIcon(itemStack, i);
                if (icon instanceof TextureAtlasSprite) {
                    ((IPatchedTextureAtlasSprite) icon).markNeedsAnimationUpdate();
                }
            }
        } else {
            final IIcon icon = itemStack.getIconIndex();
            if (icon instanceof TextureAtlasSprite) {
                ((IPatchedTextureAtlasSprite) icon).markNeedsAnimationUpdate();
            }
        }
    }
}
