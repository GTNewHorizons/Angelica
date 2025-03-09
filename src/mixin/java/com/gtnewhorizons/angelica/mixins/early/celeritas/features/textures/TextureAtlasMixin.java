package com.gtnewhorizons.angelica.mixins.early.celeritas.features.textures;

import com.google.common.collect.Iterators;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.embeddedt.embeddium.impl.util.collections.quadtree.QuadTree;
import org.embeddedt.embeddium.impl.util.collections.quadtree.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.extensions.SpriteExtension;
import org.taumc.celeritas.impl.extensions.TextureMapExtension;

import java.util.Iterator;
import java.util.Map;

@Mixin(TextureMap.class)
public class TextureAtlasMixin implements TextureMapExtension {
    @Shadow
    @Final
    private Map<String, TextureAtlasSprite> mapUploadedSprites;

    private QuadTree<TextureAtlasSprite> celeritas$quadTree;

    private int celeritas$width, celeritas$height;

    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void generateQuadTree(CallbackInfo ci, @Local(ordinal = 0) Stitcher stitcher) {
        this.celeritas$width = stitcher.getCurrentWidth();
        this.celeritas$height = stitcher.getCurrentHeight();
        Rect2i treeRect = new Rect2i(0, 0, celeritas$width, celeritas$height);
        int minSize = this.mapUploadedSprites.values().stream().mapToInt(sprite -> Math.max(sprite.getIconWidth(), sprite.getIconHeight())).min().orElse(0);
        this.celeritas$quadTree = new QuadTree<>(treeRect, minSize, this.mapUploadedSprites.values(), sprite -> new Rect2i(sprite.getOriginX(), sprite.getOriginY(), sprite.getIconWidth(), sprite.getIconHeight()));
    }

    @Override
    public QuadTree<TextureAtlasSprite> celeritas$getQuadTree() {
        return celeritas$quadTree;
    }

    @Override
    public TextureAtlasSprite celeritas$findFromUV(float u, float v) {
        int x = Math.round(u * this.celeritas$width), y = Math.round(v * this.celeritas$height);

        return this.celeritas$quadTree.find(x, y);
    }

    @ModifyExpressionValue(method = "updateAnimations", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<TextureAtlasSprite> getFilteredIterator(Iterator<TextureAtlasSprite> iterator) {
        return Iterators.filter(iterator, sprite -> ((SpriteExtension)sprite).celeritas$shouldUpdate());
    }
}
