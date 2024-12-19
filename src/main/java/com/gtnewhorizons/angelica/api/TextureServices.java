package com.gtnewhorizons.angelica.api;

import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import com.gtnewhorizons.angelica.mixins.interfaces.ITexturesCache;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;

/**
 * Exposes a method to the API that allow mods to add texture update compat
 */
@SuppressWarnings("unused")
public class TextureServices {

    @SuppressWarnings("unused")
    public static void updateBlockTextureAnimation(IIcon icon, RenderBlocks renderBlocks) {
        if (renderBlocks instanceof ITexturesCache texturesCache) {
            texturesCache.getRenderedTextures().add(icon);
        }
    }

    @SuppressWarnings("unused")
    public static void updateTextureAnimation(IIcon icon) {
        if(icon instanceof IPatchedTextureAtlasSprite patchedSprite) {
            patchedSprite.markNeedsAnimationUpdate();
        }
    }
}
