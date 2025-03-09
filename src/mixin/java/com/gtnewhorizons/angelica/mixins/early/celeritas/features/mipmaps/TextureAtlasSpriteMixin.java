package com.gtnewhorizons.angelica.mixins.early.celeritas.features.mipmaps;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.embeddedt.embeddium.api.util.ColorABGR;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.embeddedt.embeddium.impl.util.color.ColorSRGB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.extensions.SpriteExtension;

import java.util.List;

@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteMixin implements SpriteExtension {
    @Shadow
    protected List<int[][]> framesTextureData;

    @Shadow
    @Final
    private String iconName;

    private SpriteTransparencyLevel embeddium$transparencyLevel = SpriteTransparencyLevel.TRANSLUCENT;

    @Inject(method = "generateMipmaps", at = @At("HEAD"))
    private void processSprite(int level, CallbackInfo ci) {
        if (this.framesTextureData.isEmpty() || this.framesTextureData.getFirst() == null) {
            return;
        }
        embeddium$processTransparentImages(this.framesTextureData.getFirst()[0], level > 0 && !iconName.contains("leaves"));
    }

    private void embeddium$processTransparentImages(int[] nativeImage, boolean shouldRewriteColors) {
        // Calculate an average color from all pixels that are not completely transparent.
        // This average is weighted based on the (non-zero) alpha value of the pixel.
        float r = 0.0f;
        float g = 0.0f;
        float b = 0.0f;

        float totalWeight = 0.0f;

        SpriteTransparencyLevel level = SpriteTransparencyLevel.OPAQUE;

        for (int y = 0; y < nativeImage.length; y++) {
            int color = nativeImage[y];
            int alpha = ColorABGR.unpackAlpha(color);

            // Ignore all fully-transparent pixels for the purposes of computing an average color.
            if (alpha > 0) {
                if(alpha < 255) {
                    level = level.chooseNextLevel(SpriteTransparencyLevel.TRANSLUCENT);
                } else {
                    level = level.chooseNextLevel(SpriteTransparencyLevel.OPAQUE);
                }

                if (shouldRewriteColors) {
                    float weight = (float) alpha;

                    // Make sure to convert to linear space so that we don't lose brightness.
                    r += ColorSRGB.srgbToLinear(ColorABGR.unpackRed(color)) * weight;
                    g += ColorSRGB.srgbToLinear(ColorABGR.unpackGreen(color)) * weight;
                    b += ColorSRGB.srgbToLinear(ColorABGR.unpackBlue(color)) * weight;

                    totalWeight += weight;
                }
            } else {
                level = level.chooseNextLevel(SpriteTransparencyLevel.TRANSPARENT);
            }
        }

        this.embeddium$transparencyLevel = level;

        // Bail if none of the pixels are semi-transparent or we aren't supposed to rewrite colors.
        if (!shouldRewriteColors || totalWeight == 0.0f) {
            return;
        }

        r /= totalWeight;
        g /= totalWeight;
        b /= totalWeight;

        // Convert that color in linear space back to sRGB.
        // Use an alpha value of zero - this works since we only replace pixels with an alpha value of 0.
        int averageColor = ColorSRGB.linearToSrgb(r, g, b, 0);

        for (int y = 0; y < nativeImage.length; y++) {
            int color = nativeImage[y];
            int alpha = ColorABGR.unpackAlpha(color);

            // Replace the color values of pixels which are fully transparent, since they have no color data.
            if (alpha == 0) {
                nativeImage[y] = averageColor;
            }
        }
    }

    @Override
    public SpriteTransparencyLevel celeritas$getTransparencyLevel() {
        return this.embeddium$transparencyLevel;
    }
}
