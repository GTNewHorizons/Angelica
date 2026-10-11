package com.gtnewhorizons.angelica.textures.atlas;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    static Stream<Arguments> spriteClasses() {
        return Stream.of(
            Arguments.of("base sprite", TextureAtlasSprite.class, true),
            Arguments.of("plain subclass", PlainSprite.class, true),
            Arguments.of("generateMipmaps override", MipmapOverrideSprite.class, false),
            Arguments.of("setFramesTextureData override", SetFramesOverrideSprite.class, false));
    }

    @ParameterizedTest(name = "{0} -> safe={2}")
    @MethodSource("spriteClasses")
    void isSafe(String name, Class<? extends TextureAtlasSprite> spriteClass, boolean expected) {
        assertEquals(expected, MipmapSafety.isSafe(spriteClass));
    }
}
