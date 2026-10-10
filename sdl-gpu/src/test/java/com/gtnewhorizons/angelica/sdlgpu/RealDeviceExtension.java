package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREUSAGE_DEPTH_STENCIL_TARGET;

/** Owns one real SDL GPU device per test class. Runs before the class's own {@code @BeforeAll} and after its {@code @AfterAll}. */
public final class RealDeviceExtension implements BeforeAllCallback, AfterAllCallback {

    private static SdlTestRig rig;

    public record Target(long texture, int sdlFormat) {}

    public static SdlTestRig rig() {
        return rig;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        rig = SdlTestRig.acquireRealDevice();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (rig != null && rig.frameManager.isFrameActive()) rig.frameManager.endFrame();
        rig = null;
        SdlTestRig.releaseRealDevice();
    }

    public static Target colorTarget(int glId, int width, int height) {
        final long texture = rig.resourceManager.createTexture(glId, GL11.GL_TEXTURE_2D, GL11.GL_RGBA8, width, height, 1, 1);
        assertTrue(texture != 0, "color target creation failed");
        return new Target(texture, rig.resourceManager.getTextureMeta(glId).sdlFormat());
    }

    public static Target depthStencilTarget(int glId, int width, int height) {
        rig.resourceManager.cachePreferredDepthFormats();
        rig.resourceManager.createTexture(glId, GL11.GL_TEXTURE_2D, GL30.GL_DEPTH24_STENCIL8, width, height, 1, 1);
        final long texture = rig.resourceManager.ensureTextureUsage(glId, SDL_GPU_TEXTUREUSAGE_DEPTH_STENCIL_TARGET);
        assertTrue(texture != 0, "depth+stencil target creation failed");
        final int format = rig.resourceManager.getTextureMeta(glId).sdlFormat();
        assertTrue(PixelOps.isDepthStencilFormat(format), "test needs a packed depth+stencil format");
        return new Target(texture, format);
    }
}
