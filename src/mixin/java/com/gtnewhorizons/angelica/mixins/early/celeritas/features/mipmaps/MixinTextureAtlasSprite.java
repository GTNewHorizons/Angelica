package com.gtnewhorizons.angelica.mixins.early.celeritas.features.mipmaps;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.rendering.TextureAtlasType;
import com.gtnewhorizons.angelica.rendering.celeritas.SpriteExtension;
import com.gtnewhorizons.angelica.utils.MipmapGenerator;
import com.gtnewhorizons.angelica.utils.MipmapStrategies;
import com.gtnewhorizons.angelica.utils.MipmapStrategy;
import com.gtnewhorizons.angelica.utils.SpritePadding;
import me.jellysquid.mods.sodium.client.gui.options.named.MipmapGeneration;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationFrame;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.ReportedException;
import org.embeddedt.embeddium.api.util.ColorARGB;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Arrays;
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
    public List<int[][]> framesTextureData;
    @Shadow
    public AnimationMetadataSection animationMetadata;
    @Shadow
    protected int width;
    @Shadow
    protected int height;
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
    private int angelica$textureType;
    @Unique
    private float angelica$alphaCutoffBias;
    @Unique
    private boolean celeritas$hasTransparentTexels;
    @Unique
    private boolean celeritas$alwaysTranslucent;
    @Unique
    private int angelica$gutterWidth;
    @Unique
    private boolean angelica$uploaded;
    @Unique
    private int[][] angelica$animationImage;
    @Unique
    private int angelica$animationWidth;

    @Unique
    private static boolean celeritas$isAlwaysTranslucent(String name) {
        final String[] list = AngelicaConfig.alwaysTranslucentSprites;
        if (list == null) return false;
        for (String s : list) {
            if (name.equals(s)) return true;
        }
        return false;
    }

    @Unique
    private static int celeritas$classifyPixels(int[] pixels, int seen) {
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
            if (seen == SEEN_ALL) break;
        }
        return seen;
    }

    @Shadow
    public abstract void setFramesTextureData(List<int[][]> frames);

    @Inject(method = "generateMipmaps", at = @At("HEAD"), cancellable = true)
    private void celeritas$processSprite(int level, CallbackInfo ci) {
        this.celeritas$hasTransparentTexels = false;
        this.celeritas$alwaysTranslucent = celeritas$isAlwaysTranslucent(this.iconName);
        if (this.framesTextureData.isEmpty()) {
            this.angelica$animationImage = null;
            return;
        }
        if (!MipmapGeneration.usesModernMipmaps() || !angelica$updateAnimationImage(level)) {
            this.angelica$animationImage = null;
        }
        celeritas$classifyFrames();
        if (this.celeritas$alwaysTranslucent) {
            this.celeritas$transparencyLevel = SpriteTransparencyLevel.TRANSLUCENT;
        }
        if (this.angelica$animationImage != null) {
            angelica$generateAnimationMipmaps(level);
            ci.cancel();
        }
    }

    @ModifyArg(method = "loadSprite",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;getFrameTextureData([[IIII)[[I"),
        index = 0)
    private int[][] angelica$rememberAnimationImage(int[][] image) {
        if (MipmapGeneration.usesModernMipmaps()) {
            this.angelica$animationImage = image;
            this.angelica$animationWidth = this.width;
        }
        return image;
    }

    @Inject(method = {"setFramesTextureData", "clearFramesTextureData", "initSprite"}, at = @At("HEAD"))
    private void angelica$releaseAnimationImage(CallbackInfo ci) {
        this.angelica$animationImage = null;
    }

    @Unique
    private boolean angelica$updateAnimationImage(int mipLevel) {
        if (this.angelica$animationImage == null || this.width != this.angelica$animationWidth || this.height != this.width) {
            return false;
        }
        // 1.7.10 can request levels beyond the frame's power-of-two factor
        if (Integer.numberOfTrailingZeros(this.width) < mipLevel) {
            return false;
        }
        for (int frameIndex = 0; frameIndex < this.framesTextureData.size(); frameIndex++) {
            final int[][] frame = this.framesTextureData.get(frameIndex);
            if (frame == null) continue;
            for (int level = 0; level < frame.length; level++) {
                if (frame[level] == null) continue;
                final int size = (this.width >> level) * (this.height >> level);
                if (level >= this.angelica$animationImage.length || this.angelica$animationImage[level] == null
                    || frame[level].length != size || (long) (frameIndex + 1) * size > this.angelica$animationImage[level].length) {
                    return false;
                }
                System.arraycopy(frame[level], 0, this.angelica$animationImage[level], frameIndex * size, size);
            }
        }
        return true;
    }

    @Unique
    private void angelica$generateAnimationMipmaps(int level) {
        try {
            final int[][] mips = celeritas$generateMipmapData(level, this.width, this.angelica$animationImage);
            final List<int[][]> frames = new ArrayList<>(this.framesTextureData.size());
            for (int frameIndex = 0; frameIndex < this.framesTextureData.size(); frameIndex++) {
                final int[][] existing = this.framesTextureData.get(frameIndex);
                if (existing == null) continue;
                final int[][] frame = Arrays.copyOf(existing, level + 1);
                for (int mip = 0; mip <= level; mip++) {
                    final int size = (this.width >> mip) * (this.height >> mip);
                    if (frame[mip] == null || frame[mip].length != size) {
                        frame[mip] = new int[size];
                    }
                    System.arraycopy(mips[mip], frameIndex * size, frame[mip], 0, size);
                }
                frames.add(frame);
            }
            angelica$remapAnimationFrames();
            this.setFramesTextureData(frames);
        } catch (Throwable error) {
            final CrashReport report = CrashReport.makeCrashReport(error, "Generating mipmaps for animation");
            final CrashReportCategory category = report.makeCategory("Sprite being generated");
            category.addCrashSection("Sprite", this.iconName);
            category.addCrashSection("Frame count", this.framesTextureData.size());
            throw new ReportedException(report);
        } finally {
            this.angelica$animationImage = null;
        }
    }

    @Inject(method = "generateMipmaps",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;setFramesTextureData(Ljava/util/List;)V"))
    private void angelica$remapGeneratedFrames(int level, CallbackInfo ci) {
        angelica$remapAnimationFrames();
    }

    @Unique
    private void angelica$remapAnimationFrames() {
        if (this.animationMetadata == null || !this.framesTextureData.contains(null)) {
            return;
        }
        final int[] indices = new int[this.framesTextureData.size()];
        int next = 0;
        for (int i = 0; i < indices.length; i++) {
            indices[i] = this.framesTextureData.get(i) == null ? -1 : next++;
        }
        final AnimationMetadataSection metadata = this.animationMetadata;
        final List<AnimationFrame> frames = new ArrayList<>(metadata.getFrameCount());
        for (int i = 0; i < metadata.getFrameCount(); i++) {
            final int sourceIndex = metadata.getFrameIndex(i);
            final int frameIndex = sourceIndex >= 0 && sourceIndex < indices.length ? indices[sourceIndex] : -1;
            frames.add(new AnimationFrame(frameIndex,
                metadata.frameHasTime(i) ? metadata.getFrameTimeSingle(i) : -1));
        }
        this.animationMetadata = new AnimationMetadataSection(frames,
            metadata.getFrameWidth(), metadata.getFrameHeight(), metadata.getFrameTime());
    }

    @Unique
    private MipmapStrategy celeritas$resolvedStrategy() {
        if (this.celeritas$alwaysTranslucent) {
            return MipmapStrategy.MEAN;
        }

        MipmapStrategy strategy = this.celeritas$mipmapStrategy;
        boolean explicit = this.celeritas$mipmapStrategyExplicit;

        if (strategy == null) {
            strategy = MipmapStrategies.inheritedFor(this.angelica$textureType, this.iconName);
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

        if (this.angelica$animationImage != null) {
            seen = celeritas$classifyPixels(this.angelica$animationImage[0], seen);
        } else {
            for (final int[][] frame : this.framesTextureData) {
                if (frame == null || frame.length == 0 || frame[0] == null) {
                    continue;
                }
                seen = celeritas$classifyPixels(frame[0], seen);
                if (seen == SEEN_ALL) {
                    break;
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
    private int[][] celeritas$generateMipmapData(int level, int width, int[][] pixels) {
        return MipmapGenerator.generateMipLevels(level, width, pixels, celeritas$resolvedStrategy(),
            this.celeritas$hasTransparentTexels, MipmapGeneration.current(),
            this.angelica$alphaCutoffBias, this.angelica$textureType == TextureAtlasType.ITEM);
    }

    @Inject(method = "fixTransparentPixels", at = @At("HEAD"), cancellable = true)
    private void angelica$useModernTexturePreparation(int[][] pixels, CallbackInfo ci) {
        if (MipmapGeneration.usesModernMipmaps()) {
            ci.cancel();
        }
    }

    /**
     * Vanilla's anisotropic path inflates width/height by 16 and insets the UVs by 8. The custom atlas path uses
     * the selected filter mode's gutter in the stitcher slot instead.
     */
    @ModifyVariable(method = "loadSprite", at = @At("HEAD"), argsOnly = true)
    private boolean angelica$disableVanillaAnisotropicPadding(boolean useAnisotropicFiltering) {
        return false;
    }

    @ModifyVariable(method = "initSprite", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private int angelica$insetOriginX(int x) {
        return x + this.angelica$gutterWidth;
    }

    @ModifyVariable(method = "initSprite", at = @At("HEAD"), ordinal = 3, argsOnly = true)
    private int angelica$insetOriginY(int y) {
        return y + this.angelica$gutterWidth;
    }

    @Redirect(
        method = "updateAnimation",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureMipmap([[IIIIIZZ)V"))
    private void angelica$uploadPaddedFrame(int[][] frameData, int width, int height, int originX, int originY,
                                            boolean blur, boolean clamp) {
        SpritePadding.uploadPadded(frameData, width, height, originX, originY,
            this.angelica$gutterWidth, blur, clamp);
    }

    @Override
    public int angelica$getGutterWidth() {
        return this.angelica$gutterWidth;
    }

    @Override
    public void angelica$setGutterWidth(int gutter) {
        this.angelica$gutterWidth = gutter;
    }

    @Override
    public void angelica$setUploaded(boolean uploaded) {
        this.angelica$uploaded = uploaded;
    }

    @Override
    public boolean angelica$takeUploaded() {
        final boolean uploaded = this.angelica$uploaded;
        this.angelica$uploaded = false;
        return uploaded;
    }

    @Override
    public SpriteTransparencyLevel celeritas$getTransparencyLevel() {
        return this.celeritas$transparencyLevel;
    }

    @Override
    public void celeritas$setMipmapStrategy(MipmapStrategy strategy, boolean explicit) {
        this.celeritas$mipmapStrategy = strategy;
        this.celeritas$mipmapStrategyExplicit = explicit;
    }

    @Override
    public void angelica$setTextureType(int textureType) {
        this.angelica$textureType = textureType;
    }

    @Override
    public void angelica$setAlphaCutoffBias(float bias) {
        this.angelica$alphaCutoffBias = bias;
    }
}
