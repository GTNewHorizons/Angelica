package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProxyTextureLimitsTest {

    @AfterEach
    void resetContextState() {
        final ContextState st = SdlTestRig.contextState();
        st.activeTextureUnit = 0;
        st.boundTextures[0] = 0;
        st.proxyTexture.reject(0);
    }

    @Test
    void threeDimensionalProxyIsBoundedByTheVolumeLimit() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();
        final int limit = SDLGPURenderBackend.MAX_3D_TEXTURE_SIZE;

        backend.texImage3D(GL12.GL_PROXY_TEXTURE_3D, 0, GL11.GL_RGBA, limit, limit, limit, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        assertEquals(limit, backend.getTexLevelParameteri(GL12.GL_PROXY_TEXTURE_3D, 0, GL11.GL_TEXTURE_WIDTH));
        assertEquals(limit, backend.getTexLevelParameteri(GL12.GL_PROXY_TEXTURE_3D, 0, GL12.GL_TEXTURE_DEPTH));

        backend.texImage3D(GL12.GL_PROXY_TEXTURE_3D, 0, GL11.GL_RGBA, limit * 2, limit, limit, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        assertEquals(0, backend.getTexLevelParameteri(GL12.GL_PROXY_TEXTURE_3D, 0, GL11.GL_TEXTURE_WIDTH));

        backend.texImage3D(GL12.GL_PROXY_TEXTURE_3D, 0, GL11.GL_RGBA, limit, limit, limit * 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        assertEquals(0, backend.getTexLevelParameteri(GL12.GL_PROXY_TEXTURE_3D, 0, GL12.GL_TEXTURE_DEPTH));
    }

    @Test
    void everyProxyDimensionLimitIsAlsoQueryableAsADeviceLimit() {
        assertEquals(16384, SDLGPURenderBackend.deviceLimit(GL11.GL_MAX_TEXTURE_SIZE));
        assertEquals(2048, SDLGPURenderBackend.deviceLimit(GL12.GL_MAX_3D_TEXTURE_SIZE));
        assertEquals(16384, SDLGPURenderBackend.deviceLimit(GL13.GL_MAX_CUBE_MAP_TEXTURE_SIZE));

        assertEquals(16384, SDLGPURenderBackend.proxyDimensionLimit(GL11.GL_PROXY_TEXTURE_2D));
        assertEquals(16384, SDLGPURenderBackend.proxyDimensionLimit(GL11.GL_PROXY_TEXTURE_1D));
        assertEquals(2048, SDLGPURenderBackend.proxyDimensionLimit(GL12.GL_PROXY_TEXTURE_3D));
        assertEquals(16384, SDLGPURenderBackend.proxyDimensionLimit(GL13.GL_PROXY_TEXTURE_CUBE_MAP));
        assertEquals(16384, SDLGPURenderBackend.proxyDimensionLimit(GL40.GL_PROXY_TEXTURE_CUBE_MAP_ARRAY));
    }

    @Test
    void oneDimensionalArrayLayersUseTheLayerLimitNotTheDimensionLimit() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();
        final int layers = SDLGPURenderBackend.MAX_ARRAY_TEXTURE_LAYERS;

        backend.texImage2D(GL30.GL_PROXY_TEXTURE_1D_ARRAY, 0, GL11.GL_RGBA, 512, layers, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        assertEquals(512, backend.getTexLevelParameteri(GL30.GL_PROXY_TEXTURE_1D_ARRAY, 0, GL11.GL_TEXTURE_WIDTH));
        assertEquals(layers, backend.getTexLevelParameteri(GL30.GL_PROXY_TEXTURE_1D_ARRAY, 0, GL11.GL_TEXTURE_HEIGHT));

        backend.texImage2D(GL30.GL_PROXY_TEXTURE_1D_ARRAY, 0, GL11.GL_RGBA, 512, layers + 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        assertEquals(0, backend.getTexLevelParameteri(GL30.GL_PROXY_TEXTURE_1D_ARRAY, 0, GL11.GL_TEXTURE_WIDTH));
    }

    @Test
    void arrayLayerCountDoesNotHalvePerLevel() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();

        backend.texImage2D(GL30.GL_PROXY_TEXTURE_1D_ARRAY, 2, GL11.GL_RGBA, 128, 64, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        assertEquals(128, backend.getTexLevelParameteri(GL30.GL_PROXY_TEXTURE_1D_ARRAY, 2, GL11.GL_TEXTURE_WIDTH));
        assertEquals(64, backend.getTexLevelParameteri(GL30.GL_PROXY_TEXTURE_1D_ARRAY, 2, GL11.GL_TEXTURE_HEIGHT));
    }

    @Test
    void aProbedLevelIsAcceptedOnItsLevelZeroEquivalentSize() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();

        backend.texImage2D(GL11.GL_PROXY_TEXTURE_2D, 3, GL11.GL_RGBA, 1024, 512, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        assertEquals(1024, backend.getTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 3, GL11.GL_TEXTURE_WIDTH));
        assertEquals(512, backend.getTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 3, GL11.GL_TEXTURE_HEIGHT));
        assertEquals(8192, backend.getTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH));
    }

    @Test
    void aProbedLevelWhoseBaseWouldOverflowTheLimitIsRejected() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();

        backend.texImage2D(GL11.GL_PROXY_TEXTURE_2D, 2, GL11.GL_RGBA, SDLGPURenderBackend.MAX_TEXTURE_SIZE, 4, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        assertEquals(0, backend.getTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 2, GL11.GL_TEXTURE_WIDTH));

        backend.texImage2D(GL11.GL_PROXY_TEXTURE_2D, 40, GL11.GL_RGBA, 4, 4, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        assertEquals(0, backend.getTexLevelParameteri(GL11.GL_PROXY_TEXTURE_2D, 40, GL11.GL_TEXTURE_WIDTH));
    }

    @Test
    void levelZeroEquivalentSizeRejectsOverflowAndNonsense() {
        assertEquals(4, SDLGPURenderBackend.levelZeroEquivalentSize(4, 0));
        assertEquals(32, SDLGPURenderBackend.levelZeroEquivalentSize(4, 3));
        assertEquals(0, SDLGPURenderBackend.levelZeroEquivalentSize(4, -1));
        assertEquals(0, SDLGPURenderBackend.levelZeroEquivalentSize(0, 2));
        assertEquals(0, SDLGPURenderBackend.levelZeroEquivalentSize(4, 31));
        assertEquals(0, SDLGPURenderBackend.levelZeroEquivalentSize(4, 30));
        assertEquals(1 << 30, SDLGPURenderBackend.levelZeroEquivalentSize(1, 30));
    }

    @Test
    void proxyTargetsAnswerTheGlParameterDefaults() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();
        final int target = GL11.GL_PROXY_TEXTURE_2D;

        assertEquals(GL11.GL_NEAREST_MIPMAP_LINEAR, backend.getTexParameteri(target, GL11.GL_TEXTURE_MIN_FILTER));
        assertEquals(GL11.GL_LINEAR, backend.getTexParameteri(target, GL11.GL_TEXTURE_MAG_FILTER));
        assertEquals(GL11.GL_REPEAT, backend.getTexParameteri(target, GL11.GL_TEXTURE_WRAP_S));
        assertEquals(GL11.GL_REPEAT, backend.getTexParameteri(target, GL11.GL_TEXTURE_WRAP_T));
        assertEquals(GL11.GL_REPEAT, backend.getTexParameteri(target, GL12.GL_TEXTURE_WRAP_R));
        assertEquals(0, backend.getTexParameteri(target, GL12.GL_TEXTURE_BASE_LEVEL));
        assertEquals(1000, backend.getTexParameteri(target, GL12.GL_TEXTURE_MAX_LEVEL));
        assertEquals(-1000.0f, backend.getTexParameterf(target, GL12.GL_TEXTURE_MIN_LOD));
        assertEquals(1000.0f, backend.getTexParameterf(target, GL12.GL_TEXTURE_MAX_LOD));
        assertEquals(0.0f, backend.getTexParameterf(target, GL14.GL_TEXTURE_LOD_BIAS));
        assertEquals(1.0f, backend.getTexParameterf(target, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT));
    }

    @Test
    void proxyParameterDefaultsIgnoreTheBoundTexturesOwnState() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();
        final ContextState st = SdlTestRig.contextState();
        st.activeTextureUnit = 0;
        st.boundTextures[0] = 8181;

        backend.texParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        assertEquals(GL11.GL_NEAREST, backend.getTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER));
        assertEquals(GL11.GL_NEAREST_MIPMAP_LINEAR, backend.getTexParameteri(GL11.GL_PROXY_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER));
    }

    @Test
    void proxyTargetsNeverReceiveParameterWrites() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();
        final ContextState st = SdlTestRig.contextState();
        st.activeTextureUnit = 0;
        st.boundTextures[0] = 8282;

        backend.texParameteri(GL11.GL_PROXY_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        backend.texParameterf(GL11.GL_PROXY_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 4.0f);

        assertEquals(GL11.GL_NEAREST_MIPMAP_LINEAR, backend.getTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER));
        assertEquals(0.0f, backend.getTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS));
    }
}
