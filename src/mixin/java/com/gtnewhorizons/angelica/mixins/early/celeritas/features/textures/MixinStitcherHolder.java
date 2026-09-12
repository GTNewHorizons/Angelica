package com.gtnewhorizons.angelica.mixins.early.celeritas.features.textures;

import com.gtnewhorizons.angelica.rendering.celeritas.SpriteExtension;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Reserves each sprite's gutter in its stitcher slot.
 */
@Mixin(targets = "net.minecraft.client.renderer.texture.Stitcher$Holder")
public abstract class MixinStitcherHolder {

    @Shadow
    public abstract TextureAtlasSprite getAtlasSprite();

    @ModifyReturnValue(method = "getWidth", at = @At("RETURN"))
    private int angelica$reserveGutterWidth(int original) {
        return original + 2 * ((SpriteExtension) getAtlasSprite()).angelica$getGutterWidth();
    }

    @ModifyReturnValue(method = "getHeight", at = @At("RETURN"))
    private int angelica$reserveGutterHeight(int original) {
        return original + 2 * ((SpriteExtension) getAtlasSprite()).angelica$getGutterWidth();
    }
}
