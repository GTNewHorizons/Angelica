package com.gtnewhorizons.angelica.mixins.early.celeritas.features.textures;

import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import com.gtnewhorizons.angelica.rendering.celeritas.SpriteExtension;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** UV auto-marking for animation updates. Delegates to IPatchedTextureAtlasSprite. */
@Mixin(TextureAtlasSprite.class)
public abstract class MixinTextureAtlasSprite implements SpriteExtension {

    @Override
    public void celeritas$markActive() {
        ((IPatchedTextureAtlasSprite) this).markNeedsAnimationUpdate();
    }

    @Override
    public boolean celeritas$shouldUpdate() {
        return ((IPatchedTextureAtlasSprite) this).needsAnimationUpdate();
    }

    @ModifyReturnValue(method = {"getMinU", "getInterpolatedU"}, at = @At("RETURN"))
    private float celeritas$markActiveOnUVAccess(float original) {
        ((IPatchedTextureAtlasSprite) this).markNeedsAnimationUpdate();
        return original;
    }
}
