package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.sdl.SDLGPU.*;

class BlitCopyDecisionTest {

    private static ResourceManager.TextureMeta meta(int sdlFormat) {
        return new ResourceManager.TextureMeta(GL11.GL_TEXTURE_2D, GL11.GL_RGBA8, sdlFormat, 1024, 1024, 1, 1, 0);
    }

    @Test
    void sameFormatOneToOneCopies() {
        assertTrue(TextureOps.canCopyInsteadOfBlit(meta(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM), meta(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM), 1024, 1024, 1024, 1024));
    }

    @Test
    void formatMismatchBlits() {
        assertFalse(TextureOps.canCopyInsteadOfBlit(meta(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM), meta(SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM), 1024, 1024, 1024, 1024));
    }

    @Test
    void scalingBlits() {
        assertFalse(TextureOps.canCopyInsteadOfBlit(meta(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM), meta(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM), 1024, 1024, 512, 512));
    }

    @Test
    void flippedRegionBlits() {
        assertFalse(TextureOps.canCopyInsteadOfBlit(meta(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM), meta(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM), -1024, 1024, -1024, 1024));
    }

    @Test
    void missingMetaBlits() {
        assertFalse(TextureOps.canCopyInsteadOfBlit(null, meta(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM), 1024, 1024, 1024, 1024));
        assertFalse(TextureOps.canCopyInsteadOfBlit(meta(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM), null, 1024, 1024, 1024, 1024));
    }
}
