package com.gtnewhorizons.angelica.mixins.early.celeritas.features.mipmaps;

import com.gtnewhorizons.angelica.rendering.celeritas.SpriteExtension;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.embeddedt.embeddium.api.util.ColorABGR;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.embeddedt.embeddium.impl.util.color.ColorSRGB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Calculates sprite transparency level during mipmap generation. */
@Mixin(TextureAtlasSprite.class)
public abstract class MixinTextureAtlasSprite implements SpriteExtension {

    @Shadow protected List<int[][]> framesTextureData;
    @Shadow @Final private String iconName;

    @Unique private SpriteTransparencyLevel celeritas$transparencyLevel = SpriteTransparencyLevel.TRANSLUCENT;

    @Inject(method = "generateMipmaps", at = @At("HEAD"))
    private void celeritas$processSprite(int level, CallbackInfo ci) {
        if (this.framesTextureData.isEmpty() || this.framesTextureData.get(0) == null) {
            return;
        }
        celeritas$processTransparentImages(this.framesTextureData.get(0)[0], level > 0 && !iconName.contains("leaves"));
    }

    // Ordinals: OPAQUE=0, TRANSPARENT=1, TRANSLUCENT=2
    @Unique private static final int ORDINAL_OPAQUE = SpriteTransparencyLevel.OPAQUE.ordinal();
    @Unique private static final int ORDINAL_TRANSPARENT = SpriteTransparencyLevel.TRANSPARENT.ordinal();
    @Unique private static final int ORDINAL_TRANSLUCENT = SpriteTransparencyLevel.TRANSLUCENT.ordinal();
    @Unique private static final SpriteTransparencyLevel[] LEVELS_BY_ORDINAL = SpriteTransparencyLevel.values();

    @Unique
    private void celeritas$processTransparentImages(int[] nativeImage, boolean shouldRewriteColors) {
        float r = 0.0f, g = 0.0f, b = 0.0f;
        float totalWeight = 0.0f;
        // Use primitive int instead of loop-carried enum reference to avoid JDK 25 C2 SuperWord non-convergence bug
        int maxLevel = ORDINAL_OPAQUE;

        for (int y = 0; y < nativeImage.length; y++) {
            final int color = nativeImage[y];
            final int alpha = ColorABGR.unpackAlpha(color);

            if (alpha > 0) {
                if (alpha < 255) {
                    maxLevel = Math.max(maxLevel, ORDINAL_TRANSLUCENT);
                }

                if (shouldRewriteColors) {
                    final float weight = (float) alpha;
                    r += ColorSRGB.srgbToLinear(ColorABGR.unpackRed(color)) * weight;
                    g += ColorSRGB.srgbToLinear(ColorABGR.unpackGreen(color)) * weight;
                    b += ColorSRGB.srgbToLinear(ColorABGR.unpackBlue(color)) * weight;
                    totalWeight += weight;
                }
            } else {
                maxLevel = Math.max(maxLevel, ORDINAL_TRANSPARENT);
            }
        }

        this.celeritas$transparencyLevel = LEVELS_BY_ORDINAL[maxLevel];

        if (!shouldRewriteColors || totalWeight == 0.0f) {
            return;
        }

        r /= totalWeight;
        g /= totalWeight;
        b /= totalWeight;

        final int averageColor = ColorSRGB.linearToSrgb(r, g, b, 0);

        for (int y = 0; y < nativeImage.length; y++) {
            final int color = nativeImage[y];
            final int alpha = ColorABGR.unpackAlpha(color);
            if (alpha == 0) {
                nativeImage[y] = averageColor;
            }
        }
    }

    @Override
    public SpriteTransparencyLevel celeritas$getTransparencyLevel() {
        return this.celeritas$transparencyLevel;
    }
}
