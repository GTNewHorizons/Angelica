package com.gtnewhorizons.angelica.mixins.early.textures;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TextureAtlasSprite.class)
public abstract class MixinTextureAtlasSprite  {
    @Shadow
    private AnimationMetadataSection animationMetadata;

    public boolean isAnimation() {
        return animationMetadata != null && animationMetadata.getFrameCount() > 1;
    }

}
