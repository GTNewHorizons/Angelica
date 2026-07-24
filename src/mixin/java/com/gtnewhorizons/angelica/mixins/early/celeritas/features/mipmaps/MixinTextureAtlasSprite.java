package com.gtnewhorizons.angelica.mixins.early.celeritas.features.mipmaps;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.rendering.celeritas.SpriteExtension;
import com.gtnewhorizons.angelica.utils.MipmapGenerator;
import com.gtnewhorizons.angelica.utils.MipmapStrategies;
import com.gtnewhorizons.angelica.utils.MipmapStrategy;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.embeddedt.embeddium.api.util.ColorARGB;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Calculates sprite transparency and routes mipmap generation through MipmapGenerator.
 */
@Mixin(TextureAtlasSprite.class)
public abstract class MixinTextureAtlasSprite implements SpriteExtension {

    @Unique
    private static final int SEEN_TRANSPARENT = 1;
    @Unique
    private static final int SEEN_TRANSLUCENT = 2;
    @Unique
    private static final int SEEN_ALL = SEEN_TRANSPARENT | SEEN_TRANSLUCENT;
    @Unique
    private static final int SCAN_BLOCK = 256;
    @Shadow
    public boolean useAnisotropicFiltering;
    @Shadow
    public List<int[][]> framesTextureData;
    @Shadow
    @Final
    private String iconName;
    @Unique
    private SpriteTransparencyLevel celeritas$transparencyLevel = SpriteTransparencyLevel.TRANSLUCENT;
    @Unique
    private MipmapStrategy celeritas$mipmapStrategy;
    @Unique
    private boolean celeritas$mipmapStrategyExplicit;
    @Unique
    private int celeritas$textureType;
    @Unique
    private boolean celeritas$hasTransparentTexels;
    @Unique
    private boolean celeritas$alwaysTranslucent;

    @Unique
    private static boolean celeritas$isAlwaysTranslucent(String name) {
        final String[] list = AngelicaConfig.alwaysTranslucentSprites;
        if (list == null) return false;
        for (String s : list) {
            if (name.equals(s)) return true;
        }
        return false;
    }

    @Inject(method = "generateMipmaps", at = @At("HEAD"))
    private void celeritas$processSprite(int level, CallbackInfo ci) {
        this.celeritas$hasTransparentTexels = false;
        this.celeritas$alwaysTranslucent = celeritas$isAlwaysTranslucent(this.iconName);

        if (this.framesTextureData.isEmpty()) {
            return;
        }
        celeritas$classifyFrames();
        if (this.celeritas$alwaysTranslucent) {
            this.celeritas$transparencyLevel = SpriteTransparencyLevel.TRANSLUCENT;
        }
    }

    @Unique
    private MipmapStrategy celeritas$resolvedStrategy() {
        if (this.celeritas$alwaysTranslucent) {
            return MipmapStrategy.MEAN;
        }

        MipmapStrategy strategy = this.celeritas$mipmapStrategy;
        boolean explicit = this.celeritas$mipmapStrategyExplicit;

        if (strategy == null) {
            strategy = MipmapStrategies.inheritedFor(this.celeritas$textureType, this.iconName);
            explicit = false;
        }

        if (strategy != null && !explicit && !this.celeritas$hasTransparentTexels) {
            strategy = null;
        }

        return strategy;
    }

    @Unique
    private void celeritas$classifyFrames() {
        int seen = 0;

        outer:
        for (final int[][] frame : this.framesTextureData) {
            if (frame == null || frame.length == 0 || frame[0] == null) {
                continue;
            }
            final int[] pixels = frame[0];
            for (int base = 0; base < pixels.length; base += SCAN_BLOCK) {
                final int end = Math.min(base + SCAN_BLOCK, pixels.length);
                for (int i = base; i < end; i++) {
                    final int alpha = ColorARGB.unpackAlpha(pixels[i]);
                    if (alpha == 0) {
                        seen |= SEEN_TRANSPARENT;
                    } else if (alpha < 255) {
                        seen |= SEEN_TRANSLUCENT;
                    }
                }
                if (seen == SEEN_ALL) {
                    break outer;
                }
            }
        }

        this.celeritas$hasTransparentTexels = (seen & SEEN_TRANSPARENT) != 0;

        if ((seen & SEEN_TRANSLUCENT) != 0) {
            this.celeritas$transparencyLevel = SpriteTransparencyLevel.TRANSLUCENT;
        } else if ((seen & SEEN_TRANSPARENT) != 0) {
            this.celeritas$transparencyLevel = SpriteTransparencyLevel.TRANSPARENT;
        } else {
            this.celeritas$transparencyLevel = SpriteTransparencyLevel.OPAQUE;
        }
    }

    @Redirect(
        method = "generateMipmaps",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;generateMipmapData(II[[I)[[I"))
    private int[][] celeritas$generateMipmapData(int p_147949_0_, int p_147949_1_, int[][] p_147949_2_) {
        final MipmapStrategy strategy = celeritas$resolvedStrategy();
        final int border = this.useAnisotropicFiltering ? 8 : 0;

        return MipmapGenerator.generateMipLevels(p_147949_0_, p_147949_1_, p_147949_2_, strategy, this.celeritas$hasTransparentTexels, border);
    }

    @Override
    public SpriteTransparencyLevel celeritas$getTransparencyLevel() {
        return this.celeritas$transparencyLevel;
    }

    @Override
    public void celeritas$setMipmapStrategy(MipmapStrategy strategy, boolean explicit, int textureType) {
        this.celeritas$mipmapStrategy = strategy;
        this.celeritas$mipmapStrategyExplicit = explicit;
        this.celeritas$textureType = textureType;
    }
}
