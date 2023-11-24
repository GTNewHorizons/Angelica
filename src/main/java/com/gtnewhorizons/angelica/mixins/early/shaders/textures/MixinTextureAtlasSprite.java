package com.gtnewhorizons.angelica.mixins.early.shaders.textures;

import com.gtnewhorizons.angelica.client.textures.ISpriteExt;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TextureAtlasSprite.class)
public abstract class MixinTextureAtlasSprite implements ISpriteExt {
    @Shadow
    private AnimationMetadataSection animationMetadata;

    @Shadow
    protected int frameCounter;

    @Override
    public boolean isAnimation() {
        return animationMetadata != null && animationMetadata.getFrameCount() > 1;
    }

    @Override
    public int getFrame() {
        return frameCounter;
    }

    @Override
    public void callUpload(int frameIndex) {}

    @Override
    public AnimationMetadataSection getMetadata() {
        return animationMetadata;
    }
}
