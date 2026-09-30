package com.gtnewhorizons.angelica.textures.atlas;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MipmapSafetyTest {

    private static final class PlainSprite extends TextureAtlasSprite {

        PlainSprite() {
            super("plain");
        }
    }

    private static final class MipmapOverrideSprite extends TextureAtlasSprite {

        MipmapOverrideSprite() {
            super("mipmap");
        }

        @Override
        public void generateMipmaps(int level) {}
    }

    private static final class SetFramesOverrideSprite extends TextureAtlasSprite {

        SetFramesOverrideSprite() {
            super("setframes");
        }

        @Override
        public void setFramesTextureData(List<int[][]> data) {}
    }

    @Test
    void baseSpriteIsSafe() {
        assertTrue(MipmapSafety.isSafe(TextureAtlasSprite.class));
    }

    @Test
    void plainSubclassIsSafe() {
        assertTrue(MipmapSafety.isSafe(PlainSprite.class));
    }

    @Test
    void generateMipmapsOverrideIsUnsafe() {
        assertFalse(MipmapSafety.isSafe(MipmapOverrideSprite.class));
    }

    @Test
    void setFramesTextureDataOverrideIsUnsafe() {
        assertFalse(MipmapSafety.isSafe(SetFramesOverrideSprite.class));
    }
}
