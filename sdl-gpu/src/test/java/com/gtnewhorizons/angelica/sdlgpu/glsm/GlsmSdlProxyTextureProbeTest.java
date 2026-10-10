package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.LIGHTMAP_SIZE;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.SIZE;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.TEXEL;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.describe;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.pixelAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlProxyTextureProbeTest {

    private static final int[] PROXY_TARGETS = {
        0x8063, 0x8064, 0x8070, 0x851B, 0x8C19, 0x8C1B, 0x84F7, 0x900B, 0x9101, 0x9103
    };

    private static int baseTexture;
    private static int lightmapTexture;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.beginFrame();
        baseTexture = GlsmSdlHeadlessRig.createSolidTexture(TEXEL);
        lightmapTexture = GlsmSdlHeadlessRig.createLightmapTexture(false);
        GlsmSdlHeadlessRig.endFrame();
    }

    @Test
    void vanillaMaximumTextureSizeProbeLeavesTheBoundLightmapIntact() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, lightmapTexture);

        final ResourceManager rm = GlsmSdlHeadlessRig.resourceManager();
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

        final ResourceManager rm = GlsmSdlHeadlessRig.resourceManager();
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
        GlsmSdlHeadlessRig.bindBaseTexture(baseTexture);
        GlsmSdlHeadlessRig.enableLightmapUnit(lightmapTexture, true);

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        assertEquals(16384, vanillaGetGLMaximumTextureSize());
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

        GlsmSdlHeadlessRig.terrainRenderState();
        GlsmSdlHeadlessRig.quadTwoUnits((short) 240);

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
}
