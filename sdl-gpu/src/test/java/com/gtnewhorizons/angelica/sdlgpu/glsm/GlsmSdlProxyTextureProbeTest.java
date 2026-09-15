package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.SIZE;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.describe;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.pixelAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlProxyTextureProbeTest {

    private static final int TEXEL = 0xFFFF4020;
    private static final int LIGHTMAP_SIZE = 16;

    private static final int[] PROXY_TARGETS = {
        0x8063, 0x8064, 0x8070, 0x851B, 0x8C19, 0x8C1B, 0x84F7, 0x900B, 0x9101, 0x9103
    };

    private static int baseTexture;
    private static int lightmapTexture;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
        GlsmSdlHeadlessRig.beginFrame();
        baseTexture = GlsmSdlHeadlessRig.createSolidTexture(TEXEL);
        lightmapTexture = createLightmapTexture();
        GlsmSdlHeadlessRig.endFrame();
    }

    @Test
    void vanillaMaximumTextureSizeProbeLeavesTheBoundLightmapIntact() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, lightmapTexture);

        final ResourceManager rm = resourceManager();
        final long handleBefore = rm.getTextureHandle(lightmapTexture);

        assertEquals(16384, vanillaGetGLMaximumTextureSize());

        final ResourceManager.TextureMeta meta = rm.getTextureMeta(lightmapTexture);
        assertNotNull(meta, "lightmap meta was dropped by the proxy probe");
        assertEquals(LIGHTMAP_SIZE, meta.width(), "lightmap width after probe");
        assertEquals(LIGHTMAP_SIZE, meta.height(), "lightmap height after probe");
        assertEquals(handleBefore, rm.getTextureHandle(lightmapTexture), "lightmap handle after probe");
        assertEquals(LIGHTMAP_SIZE, GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH));

        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    @Test
    void proxyTargetCallsLeaveTheBoundTextureIntact() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, lightmapTexture);

        final ResourceManager rm = resourceManager();
        final long handleBefore = rm.getTextureHandle(lightmapTexture);
        final ResourceManager.TextureMeta before = rm.getTextureMeta(lightmapTexture);
        assertNotNull(before, "lightmap meta missing before the proxy calls");

        for (int target : PROXY_TARGETS) {
            final String where = "0x" + Integer.toHexString(target);
            GLStateManager.glTexParameteri(target, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GLStateManager.glTexParameterf(target, GL12.GL_TEXTURE_MIN_LOD, 4.0f);
            GLStateManager.glTexSubImage2D(target, 0, 0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GLStateManager.glCopyTexSubImage2D(target, 0, 0, 0, 0, 0, 1, 1);
            GLStateManager.glCopyTexImage2D(target, 0, GL11.GL_RGBA, 0, 0, 4, 4, 0);
            GLStateManager.glGenerateMipmap(target);

            final ResourceManager.TextureMeta after = rm.getTextureMeta(lightmapTexture);
            assertNotNull(after, where);
            assertEquals(before.width(), after.width(), where);
            assertEquals(before.height(), after.height(), where);
            assertEquals(before.levels(), after.levels(), where);
            assertEquals(before.glFormat(), after.glFormat(), where);
            assertEquals(handleBefore, rm.getTextureHandle(lightmapTexture), where);
        }

        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    @Test
    void proxyProbeDuringTerrainDrawStillShowsTheTexel() {
        bindBaseTexture();
        enableLightmapUnit();

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        assertEquals(16384, vanillaGetGLMaximumTextureSize());
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

        GlsmSdlHeadlessRig.terrainRenderState();
        quadTwoUnits();

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        final int center = pixelAt(pixels, SIZE, SIZE / 2, SIZE / 2);
        final int corner = pixelAt(pixels, SIZE, 2, 2);
        assertEquals(TEXEL, center, () -> "center " + describe(center));
        assertEquals(TEXEL, corner, () -> "corner " + describe(corner));
    }

    private static int vanillaGetGLMaximumTextureSize() {
        for (int i = 16384; i > 0; i >>= 1) {
            GLStateManager.glTexImage2D(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_RGBA, i, i, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            final int j = GLStateManager.glGetTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            if (j != 0) {
                return i;
            }
        }
        return -1;
    }

    private static ResourceManager resourceManager() {
        return Reflect.get(BackendManager.RENDER_BACKEND, "resourceManager");
    }

    private static int createLightmapTexture() {
        final int id = GLStateManager.glGenTextures();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, LIGHTMAP_SIZE, LIGHTMAP_SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        final ByteBuffer texels = MemoryUtil.memAlloc(LIGHTMAP_SIZE * LIGHTMAP_SIZE * 4);
        try {
            for (int i = 0; i < LIGHTMAP_SIZE * LIGHTMAP_SIZE; i++) {
                texels.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) 255);
            }
            texels.flip();
            GLStateManager.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, LIGHTMAP_SIZE, LIGHTMAP_SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
        } finally {
            MemoryUtil.memFree(texels);
        }
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        return id;
    }

    private static void bindBaseTexture() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, baseTexture);
        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void enableLightmapUnit() {
        GlsmSdlHeadlessRig.enableLightmapUnit(lightmapTexture, true);
    }

    private static void quadTwoUnits() {
        GLStateManager.glBegin(GL11.GL_QUADS);
        GlsmSdlHeadlessRig.emitVertex(-1.0f, -1.0f, 0.0f, 0.0f, true, (short) 240);
        GlsmSdlHeadlessRig.emitVertex(1.0f, -1.0f, 1.0f, 0.0f, true, (short) 240);
        GlsmSdlHeadlessRig.emitVertex(1.0f, 1.0f, 1.0f, 1.0f, true, (short) 240);
        GlsmSdlHeadlessRig.emitVertex(-1.0f, 1.0f, 0.0f, 1.0f, true, (short) 240);
        GLStateManager.glEnd();
    }
}
