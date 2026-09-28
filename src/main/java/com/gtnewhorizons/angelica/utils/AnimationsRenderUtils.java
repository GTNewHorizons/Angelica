package com.gtnewhorizons.angelica.utils;

import java.util.ArrayDeque;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import com.gtnewhorizons.angelica.mixins.interfaces.ITexturesCache;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

public class AnimationsRenderUtils {

    private static final ThreadLocal<SpriteCapture> SPRITE_CAPTURE = new ThreadLocal<>();

    /** Collects visibility notifications while baking a mesh, for replay when that mesh is reused. */
    public static final class SpriteCapture implements AutoCloseable {
        private final ReferenceOpenHashSet<IPatchedTextureAtlasSprite> sprites = new ReferenceOpenHashSet<>();
        private SpriteCapture parent;

        private SpriteCapture() {
            parent = SPRITE_CAPTURE.get();
            SPRITE_CAPTURE.set(this);
        }

        public void markUsed() {
            for (IPatchedTextureAtlasSprite sprite : sprites) sprite.angelica$markNeedsAnimationUpdate();
        }

        @Override
        public void close() {
            SPRITE_CAPTURE.set(parent);
            if (parent != null) parent.sprites.addAll(sprites);
            parent = null;
        }
    }

    public static SpriteCapture captureSprites() {
        return new SpriteCapture();
    }

    public static void recordSpriteUsage(IPatchedTextureAtlasSprite sprite) {
        final SpriteCapture capture = SPRITE_CAPTURE.get();
        if (capture != null) capture.sprites.add(sprite);
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordStateCommandOnce(new SpriteUsage(sprite));
        }
    }

    private record SpriteUsage(IPatchedTextureAtlasSprite sprite) implements DisplayListCommand {
        @Override
        public void execute() {
            sprite.angelica$markNeedsAnimationUpdate();
        }
    }

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
                patchedSprite.angelica$markNeedsAnimationUpdate();
            }
        }
    }

    private final static ThreadLocal<ArrayDeque<ITexturesCache>> TEXTURE_CACHE_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    public static void onSpriteUsed(IPatchedTextureAtlasSprite sprite) {
        ArrayDeque<ITexturesCache> stack = TEXTURE_CACHE_STACK.get();

        if (stack == null || stack.isEmpty()) {
            // icon was used outside of chunk building, it's probably an item in an inventory or something
            sprite.angelica$markNeedsAnimationUpdate();
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
