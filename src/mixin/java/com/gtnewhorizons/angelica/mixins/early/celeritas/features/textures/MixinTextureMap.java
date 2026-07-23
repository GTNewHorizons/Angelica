package com.gtnewhorizons.angelica.mixins.early.celeritas.features.textures;

import com.gtnewhorizons.angelica.mixins.interfaces.TextureMetadataExtension;
import com.gtnewhorizons.angelica.rendering.celeritas.SpriteExtension;
import com.gtnewhorizons.angelica.rendering.celeritas.TextureMapExtension;
import com.gtnewhorizons.angelica.utils.MipmapStrategies;
import com.gtnewhorizons.angelica.utils.MipmapStrategy;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.data.TextureMetadataSection;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import org.embeddedt.embeddium.impl.util.collections.quadtree.QuadTree;
import org.embeddedt.embeddium.impl.util.collections.quadtree.Rect2i;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
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
    public int mipmapLevels;
    @Shadow
    @Final
    private int textureType;
    @Unique
    private QuadTree<TextureAtlasSprite> celeritas$quadTree;
    @Unique
    private int celeritas$width;
    @Unique
    private int celeritas$height;

    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void angelica$applyAtlasSettings(CallbackInfo ci) {
        if ((Object) this == Minecraft.getMinecraft().getTextureMapBlocks()) {
            SodiumGameOptions.applyAtlasSettings();
        }
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
    @Redirect(
        method = "registerIcons",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;registerBlockIcons(Lnet/minecraft/client/renderer/texture/IIconRegister;)V"))
    private void angelica$trackBlockIcons(Block block, IIconRegister reg) {
        MipmapStrategies.beginBlock(block);
        try {
            block.registerBlockIcons(reg);
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

    @ModifyArg(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;loadSprite([Ljava/awt/image/BufferedImage;Lnet/minecraft/client/resources/data/AnimationMetadataSection;Z)V"))
    private boolean angelica$forceSpritePadding(boolean useAnisotropicFiltering) {
        if ((Object) this != Minecraft.getMinecraft().getTextureMapBlocks()) {
            return useAnisotropicFiltering;
        }
        return useAnisotropicFiltering || SodiumGameOptions.needsForcedSpritePadding();
    }

    @ModifyExpressionValue(
        method = "initMissingImage",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/texture/TextureMap;anisotropicFiltering:I",
            opcode = Opcodes.GETFIELD))
    private int angelica$forceMissingImagePadding(int anisotropicFiltering) {
        if ((Object) this != Minecraft.getMinecraft().getTextureMapBlocks()) {
            return anisotropicFiltering;
        }
        final int forced = SodiumGameOptions.needsForcedSpritePadding() ? 2 : 1;
        return Math.max(SodiumGameOptions.resolvedAnisotropicFiltering(), forced);
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
            final int padding = sprite.useAnisotropicFiltering ? 16 : 0;
            minDimension = Math.min(minDimension, Math.min(sprite.getIconWidth(), sprite.getIconHeight()) - padding);
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
