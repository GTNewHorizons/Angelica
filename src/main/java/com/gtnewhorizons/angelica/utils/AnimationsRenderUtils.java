package com.gtnewhorizons.angelica.utils;

import java.util.ArrayDeque;

import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import com.gtnewhorizons.angelica.mixins.interfaces.ITexturesCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

public class AnimationsRenderUtils {

    public static void markBlockTextureForUpdate(IIcon icon) {
        markBlockTextureForUpdate(icon, null);
    }

    public static void markBlockTextureForUpdate(IIcon icon, IBlockAccess blockAccess) {
        final TextureMap textureMap = Minecraft.getMinecraft().getTextureMapBlocks();
        final TextureAtlasSprite textureAtlasSprite = textureMap.getAtlasSprite(icon.getIconName());

        if (textureAtlasSprite != null && textureAtlasSprite.hasAnimationMetadata()) {
            // null if called by anything but chunk render cache update (for example to get blocks rendered as items in
            // inventory)
            if (blockAccess instanceof ITexturesCache texturesCache) {
                texturesCache.getRenderedTextures().add(textureAtlasSprite);
            } else if(textureAtlasSprite instanceof IPatchedTextureAtlasSprite patchedSprite){
                patchedSprite.markNeedsAnimationUpdate();
            }
        }
    }

    private final static ThreadLocal<ArrayDeque<ITexturesCache>> TEXTURE_CACHE_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    public static void onSpriteUsed(IPatchedTextureAtlasSprite sprite) {
        ArrayDeque<ITexturesCache> stack = TEXTURE_CACHE_STACK.get();

        if (stack == null || stack.isEmpty()) {
            // icon was used outside of chunk building, it's probably an item in an inventory or something
            sprite.markNeedsAnimationUpdate();
            return;
        }

        stack.peek().track(sprite);
    }

    public static void pushCache(ITexturesCache cache) {
        TEXTURE_CACHE_STACK.get().push(cache);
    }

    public static void popCache() {
        TEXTURE_CACHE_STACK.get().pop();
    }
}
