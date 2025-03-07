package com.gtnewhorizons.angelica.mixins.early.angelica.textures;

import com.gtnewhorizons.angelica.mixins.interfaces.ISpriteExt;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.image.BufferedImage;
import java.util.List;

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

    @Shadow
    public List<int[][]> framesTextureData;

    private int[] spriteData;

    @Inject(method="loadSprite", at = @At("RETURN"))
    private void injectLoadSprite(BufferedImage[] bufferedImages, AnimationMetadataSection p_147964_2_, boolean p_147964_3_, CallbackInfo ci)
    {
        BufferedImage bufferedImage = bufferedImages[0];
        spriteData = new int[bufferedImage.getWidth() * bufferedImage.getHeight()];
        bufferedImage.getRGB(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(), spriteData, 0, bufferedImage.getWidth());
        for(int i = 0; i < spriteData.length; i++)
        {
            int pixel = spriteData[i];
            int b = pixel & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int r = (pixel >> 16) & 0xFF;
            int a = (pixel >> 24) & 0xFF;
            spriteData[i] = r | (g << 8) | (b << 16) | (a << 24);
        }
    }

    @Override
    public int[] getSpriteData()
    {
        return spriteData;
    }
}
