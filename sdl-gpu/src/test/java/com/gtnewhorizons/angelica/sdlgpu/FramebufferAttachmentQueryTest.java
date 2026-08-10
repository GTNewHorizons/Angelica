package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.sdlgpu.resource.FboState;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static com.gtnewhorizons.angelica.sdlgpu.SDLGPURenderBackend.defaultFramebufferAttachmentParameteri;
import static com.gtnewhorizons.angelica.sdlgpu.SDLGPURenderBackend.fboAttachmentParameteri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_D24_UNORM;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;

class FramebufferAttachmentQueryTest {

    private static final int COLOR = SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM;

    private static int defaultFb(int attachment, int pname, int depthFormat) {
        return defaultFramebufferAttachmentParameteri(attachment, pname, COLOR, depthFormat);
    }

    @Test
    void defaultFramebufferReportsEightStencilBits() {
        assertEquals(8, defaultFb(GL11.GL_STENCIL, GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE, SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT));
        assertEquals(8, defaultFb(GL11.GL_STENCIL, GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE, SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT));
    }

    @Test
    void defaultFramebufferReportsDepthAndColorSizes() {
        assertEquals(24, defaultFb(GL11.GL_DEPTH, GL30.GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE, SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT));
        assertEquals(32, defaultFb(GL11.GL_DEPTH, GL30.GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE, SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT));
        assertEquals(8, defaultFb(GL11.GL_BACK_LEFT, GL30.GL_FRAMEBUFFER_ATTACHMENT_RED_SIZE, SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT));
        assertEquals(8, defaultFb(GL11.GL_BACK_LEFT, GL30.GL_FRAMEBUFFER_ATTACHMENT_ALPHA_SIZE, SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT));
    }

    @Test
    void defaultFramebufferWithoutDepthReportsNone() {
        assertEquals(0, defaultFb(GL11.GL_STENCIL, GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE, 0));
        assertEquals(0, defaultFb(GL11.GL_DEPTH, GL30.GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE, 0));
        assertEquals(GL11.GL_NONE, defaultFb(GL11.GL_DEPTH, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE, 0));
        assertEquals(GL30.GL_FRAMEBUFFER_DEFAULT, defaultFb(GL11.GL_BACK_LEFT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE, 0));
    }

    @Test
    void packedDepthStencilAttachmentReportsBothAspects() {
        final FboState fbo = new FboState();
        fbo.depthGlId = 42;
        fbo.depthTexture = 0xDDDDL;
        fbo.depthFormat = SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT;

        for (int attachment : new int[]{ GL30.GL_DEPTH_ATTACHMENT, GL30.GL_STENCIL_ATTACHMENT, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL11.GL_STENCIL }) {
            assertEquals(8, fboAttachmentParameteri(fbo, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE), "attachment " + attachment);
        }
        assertEquals(24, fboAttachmentParameteri(fbo, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE));
        assertEquals(42, fboAttachmentParameteri(fbo, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME));
        assertEquals(GL11.GL_TEXTURE, fboAttachmentParameteri(fbo, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE));
    }

    @Test
    void depthOnlyAttachmentReportsNoStencil() {
        final FboState fbo = new FboState();
        fbo.depthGlId = 7;
        fbo.depthTexture = 0xDDDDL;
        fbo.depthFormat = SDL_GPU_TEXTUREFORMAT_D24_UNORM;

        assertEquals(0, fboAttachmentParameteri(fbo, GL11.GL_STENCIL, GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE));
        assertEquals(24, fboAttachmentParameteri(fbo, GL11.GL_DEPTH, GL30.GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE));
    }

    @Test
    void missingAttachmentReportsNone() {
        final FboState fbo = new FboState();
        assertEquals(GL11.GL_NONE, fboAttachmentParameteri(fbo, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE));
        assertEquals(0, fboAttachmentParameteri(fbo, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE));
        assertEquals(0, fboAttachmentParameteri(null, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE));
    }

    @Test
    void colorAttachmentReportsChannelSizes() {
        final FboState fbo = new FboState();
        fbo.colorGlIds[1] = 11;
        fbo.colorTextures[1] = 0xCCCCL;
        fbo.colorFormats[1] = SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;

        assertEquals(8, fboAttachmentParameteri(fbo, GL30.GL_COLOR_ATTACHMENT0 + 1, GL30.GL_FRAMEBUFFER_ATTACHMENT_GREEN_SIZE));
        assertEquals(11, fboAttachmentParameteri(fbo, GL30.GL_COLOR_ATTACHMENT0 + 1, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME));
        assertEquals(GL11.GL_NONE, fboAttachmentParameteri(fbo, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE));
    }
}
