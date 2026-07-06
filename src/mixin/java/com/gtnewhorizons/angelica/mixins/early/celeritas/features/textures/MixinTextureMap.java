package com.gtnewhorizons.angelica.mixins.early.celeritas.features.textures;

import com.gtnewhorizons.angelica.rendering.celeritas.TextureMapExtension;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.embeddedt.embeddium.impl.util.collections.quadtree.QuadTree;
import org.embeddedt.embeddium.impl.util.collections.quadtree.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** QuadTree for sprite UV lookup. */
@Mixin(TextureMap.class)
public class MixinTextureMap implements TextureMapExtension {

    @Shadow @Final private Map<String, TextureAtlasSprite> mapUploadedSprites;

    @Unique private QuadTree<TextureAtlasSprite> celeritas$quadTree;
    @Unique private int celeritas$width;
    @Unique private int celeritas$height;

    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void celeritas$generateQuadTree(CallbackInfo ci, @Local(ordinal = 0) Stitcher stitcher) {
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
