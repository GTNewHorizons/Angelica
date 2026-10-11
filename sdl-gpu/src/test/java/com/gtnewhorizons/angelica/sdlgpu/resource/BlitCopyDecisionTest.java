package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.sdl.SDLGPU.*;

class BlitCopyDecisionTest {

    private static ResourceManager.TextureMeta meta(int sdlFormat) {
        return new ResourceManager.TextureMeta(GL11.GL_TEXTURE_2D, GL11.GL_RGBA8, sdlFormat, 1024, 1024, 1, 1, 0);
    }

    private static final int RGBA = SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;
    private static final int BGRA = SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM;

    private static Arguments blit(String name, boolean canCopy, ResourceManager.TextureMeta src, ResourceManager.TextureMeta dst, int srcW, int srcH, int dstW, int dstH) {
        return Arguments.of(name, canCopy, src, dst, srcW, srcH, dstW, dstH);
    }

    static Stream<Arguments> blits() {
        return Stream.of(
            blit("same format, 1:1: copies", true, meta(RGBA), meta(RGBA), 1024, 1024, 1024, 1024),
            blit("format mismatch: blits", false, meta(RGBA), meta(BGRA), 1024, 1024, 1024, 1024),
            blit("scaling: blits", false, meta(RGBA), meta(RGBA), 1024, 1024, 512, 512),
            blit("flipped region: blits", false, meta(RGBA), meta(RGBA), -1024, 1024, -1024, 1024),
            blit("missing source meta: blits", false, null, meta(RGBA), 1024, 1024, 1024, 1024),
            blit("missing destination meta: blits", false, meta(RGBA), null, 1024, 1024, 1024, 1024));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blits")
    void canCopyInsteadOfBlit(String name, boolean canCopy, ResourceManager.TextureMeta src, ResourceManager.TextureMeta dst, int srcW, int srcH, int dstW, int dstH) {
        assertEquals(canCopy, TextureOps.canCopyInsteadOfBlit(src, dst, srcW, srcH, dstW, dstH));
    }
}
