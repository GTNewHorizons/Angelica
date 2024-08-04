package com.gtnewhorizons.angelica.mixins.early.angelica.animation;

import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.IIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderItem.class)
public class MixinRenderItem {

    /**
     * Some mods may call it to render their internally stored icons, so we make sure we mark those for an update
     */
    @Inject(method = "renderIcon", at = @At("HEAD"))
    private void angelica$beforeRenderIcon(int p_94149_1_, int p_94149_2_, IIcon icon, int p_94149_4_, int p_94149_5_,
            CallbackInfo ci) {
        if (icon instanceof TextureAtlasSprite) {
            ((IPatchedTextureAtlasSprite) icon).markNeedsAnimationUpdate();
        }
    }
}
