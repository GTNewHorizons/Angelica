package com.gtnewhorizons.angelica.mixins.early.celeritas.features.textures;

import com.gtnewhorizons.angelica.mixins.interfaces.TextureMetadataExtension;
import com.gtnewhorizons.angelica.rendering.celeritas.SpriteExtension;
import com.gtnewhorizons.angelica.rendering.celeritas.TextureMapExtension;
import com.google.common.collect.Lists;
import com.gtnewhorizons.angelica.utils.MipmapStrategies;
import com.gtnewhorizons.angelica.utils.MipmapStrategy;
import com.gtnewhorizons.angelica.utils.SpritePadding;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.data.TextureMetadataSection;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import org.embeddedt.embeddium.impl.util.collections.quadtree.QuadTree;
import org.embeddedt.embeddium.impl.util.collections.quadtree.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * QuadTree for sprite UV lookup, plus per-sprite mipmap strategy assignment.
 */
@Mixin(TextureMap.class)
public class MixinTextureMap implements TextureMapExtension {

    @Shadow
    @Final
    public Map<String, TextureAtlasSprite> mapUploadedSprites;
    @Shadow
    @Final
    public Map<String, TextureAtlasSprite> mapRegisteredSprites;
    @Shadow
    public int mipmapLevels;
    @Shadow
    @Final
    private int textureType;
    @Shadow
    @Final
    private TextureAtlasSprite missingImage;
    @Unique
    private QuadTree<TextureAtlasSprite> celeritas$quadTree;
    @Unique
    private int celeritas$width;
    @Unique
    private int celeritas$height;

    @Inject(method = { "loadTexture", "loadTextureAtlas" }, at = @At("HEAD"))
    private void angelica$applyAtlasSettings(CallbackInfo ci) {
        if ((Object) this == Minecraft.getMinecraft().getTextureMapBlocks()) {
            SodiumGameOptions.applyAtlasSettings();
        }
    }

    @Inject(
        method = "loadTextureAtlas",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/Stitcher;doStitch()V"))
    private void angelica$assignGutters(CallbackInfo ci) {
        final boolean blocksAtlas = (Object) this == Minecraft.getMinecraft().getTextureMapBlocks();
        final int gutter = SpritePadding.gutterFor(this.mipmapLevels, false);

        for (TextureAtlasSprite sprite : this.mapRegisteredSprites.values()) {
            ((SpriteExtension) sprite).angelica$setGutterWidth(gutter);
        }
        ((SpriteExtension) this.missingImage).angelica$setGutterWidth(gutter);
    }

    /**
     * The block atlas re-gathers its icon list on every reload, so the previous attribution is stale.
     */
    @Inject(method = "registerIcons", at = @At("HEAD"))
    private void angelica$resetMipmapStrategies(CallbackInfo ci) {
        if (this.textureType == 0) {
            MipmapStrategies.reset();
        }
    }

    /**
     * Brackets each block's icon registration so the sprites it registers can be attributed to it.
     */
    @WrapOperation(
        method = "registerIcons",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;registerBlockIcons(Lnet/minecraft/client/renderer/texture/IIconRegister;)V"))
    private void angelica$trackBlockIcons(Block block, IIconRegister reg, Operation<Void> original) {
        MipmapStrategies.beginBlock(block);
        try {
            original.call(block, reg);
        } finally {
            MipmapStrategies.endBlock();
        }
    }

    /**
     * Attributes the icon to whichever block is registering it.
     */
    @Inject(method = "registerIcon", at = @At("HEAD"))
    private void angelica$recordSpriteOwner(String p_94245_1_, CallbackInfoReturnable<IIcon> cir) {
        MipmapStrategies.recordSprite(this.textureType, p_94245_1_);
    }

    /**
     * Forces vanilla's unpadded branch.
     */
    @Inject(method = "initMissingImage", at = @At("HEAD"), cancellable = true)
    private void angelica$buildMissingImage(CallbackInfo ci) {
        final int size = (int) Math.sqrt(TextureUtil.missingTextureData.length);

        this.missingImage.useAnisotropicFiltering = false;
        this.missingImage.setIconWidth(size);
        this.missingImage.setIconHeight(size);

        final int[][] mips = new int[this.mipmapLevels + 1][];
        mips[0] = TextureUtil.missingTextureData;
        this.missingImage.setFramesTextureData(Lists.newArrayList(new int[][][] { mips }));
        ci.cancel();
    }

    @Redirect(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureMipmap([[IIIIIZZ)V"))
    private void angelica$uploadPaddedSprite(int[][] frameData, int width, int height, int originX, int originY,
        boolean blur, boolean clamp, @Local(ordinal = 0) TextureAtlasSprite sprite) {
        SpritePadding.uploadPadded(frameData, width, height, originX, originY,
            ((SpriteExtension) sprite).angelica$getGutterWidth(), blur, clamp);
    }

    /**
     * Assigns the sprite's mipmap strategy.
     */
    @Inject(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;loadSprite([Ljava/awt/image/BufferedImage;Lnet/minecraft/client/resources/data/AnimationMetadataSection;Z)V"))
    private void celeritas$assignMipmapStrategy(CallbackInfo ci, @Local(ordinal = 0) TextureAtlasSprite sprite,
                                                @Local(ordinal = 0) TextureMetadataSection metadata) {
        final MipmapStrategy declared = metadata != null
            ? ((TextureMetadataExtension) metadata).angelica$getMipmapStrategy()
            : null;
        if (declared != null) {
            ((SpriteExtension) sprite).celeritas$setMipmapStrategy(declared, true, this.textureType);
        } else {
            ((SpriteExtension) sprite).celeritas$setMipmapStrategy(
                MipmapStrategies.inheritedFor(this.textureType, sprite.getIconName()), false, this.textureType);
        }
    }

    @Unique
    private int celeritas$unpaddedMipmapCeiling() {
        int minDimension = Integer.MAX_VALUE;
        for (TextureAtlasSprite sprite : this.mapUploadedSprites.values()) {
            minDimension = Math.min(minDimension, Math.min(sprite.getIconWidth(), sprite.getIconHeight()));
        }
        return minDimension == Integer.MAX_VALUE ? Integer.MAX_VALUE : MathHelper.calculateLogBaseTwo(minDimension);
    }

    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void celeritas$generateQuadTree(CallbackInfo ci, @Local(ordinal = 0) Stitcher stitcher) {
        if ((Object) this == Minecraft.getMinecraft().getTextureMapBlocks()) {
            SodiumGameOptions.recordAtlasMipmapClamp(Minecraft.getMinecraft().gameSettings.mipmapLevels,
                Math.min(this.mipmapLevels, celeritas$unpaddedMipmapCeiling()));
        }

        this.celeritas$width = stitcher.getCurrentWidth();
        this.celeritas$height = stitcher.getCurrentHeight();

        final Rect2i treeRect = new Rect2i(0, 0, celeritas$width, celeritas$height);
        final int minSize = this.mapUploadedSprites.values().stream().mapToInt(sprite -> Math.max(sprite.getIconWidth(), sprite.getIconHeight())).min().orElse(0);
        this.celeritas$quadTree = new QuadTree<>(treeRect, minSize, this.mapUploadedSprites.values(), sprite -> new Rect2i(sprite.getOriginX(), sprite.getOriginY(), sprite.getIconWidth(), sprite.getIconHeight()));
    }

    @Override
    public TextureAtlasSprite celeritas$findFromUV(float u, float v) {
        if (celeritas$quadTree == null) return null;
        return this.celeritas$quadTree.find(Math.round(u * this.celeritas$width), Math.round(v * this.celeritas$height));
    }
}
