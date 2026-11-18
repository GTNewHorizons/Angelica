package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.ffp.VertexKey;
import com.gtnewhorizons.angelica.glsm.states.PixelStoreState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.stream.Stream;

import static com.gtnewhorizons.angelica.glsm.GlTestSupport.drainErrors;
import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.HINT_TARGETS;
import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.assertEnableRoundTrip;
import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.drainClientAttribStack;
import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.hex;
import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.legacyPnames;
import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.popAttribTo;
import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.setEnabled;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
public class GLSM_LegacyStackQueries_GLTest {

    private static final int[] LEGACY_PNAMES = legacyPnames();

    private static void assumeDriverRejectsLegacyPnames() {
        drainErrors();
        GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH);
        assumeTrue(GL11.glGetError() != GL11.GL_NO_ERROR, "driver accepts GL_ATTRIB_STACK_DEPTH, so a clean error state proves nothing");
    }

    private static int pushToLimit(int depthPname, int maxPname) {
        final int max = GLSMQueries.glGetInteger(maxPname);
        for (int i = 0; i < max + 4; i++) {
            GLSMMatrices.glPushMatrix();
        }
        return GLSMQueries.glGetInteger(depthPname);
    }

    private static void popToBase(int depthPname) {
        for (int i = 0; i < 128 && GLSMQueries.glGetInteger(depthPname) > 1; i++) {
            GLSMMatrices.glPopMatrix();
        }
    }

    @Test
    void attribDepthTracksPushAndPop() {
        final int base = GLSMQueries.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH);
        try {
            GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT);
            assertEquals(base + 1, GLSMQueries.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH));
            GLSMAttribStack.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
            assertEquals(base + 2, GLSMQueries.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH));
            GLSMAttribStack.glPopAttrib();
            assertEquals(base + 1, GLSMQueries.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH));
            GLSMAttribStack.glPopAttrib();
            assertEquals(base, GLSMQueries.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH));
            assertEquals(GLStateManager.MAX_ATTRIB_STACK_DEPTH, GLSMQueries.glGetInteger(GL11.GL_MAX_ATTRIB_STACK_DEPTH));
        } finally {
            popAttribTo(base);
        }
    }

    @Test
    void clientAttribDepthTracksPushAndPop() {
        try {
            assertEquals(0, GLSMQueries.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH));
            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            assertEquals(1, GLSMQueries.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH));
            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            assertEquals(2, GLSMQueries.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH));
            GLSMAttribStack.glPopClientAttrib();
            assertEquals(1, GLSMQueries.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH));
            GLSMAttribStack.glPopClientAttrib();
            assertEquals(0, GLSMQueries.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH));
            GLSMAttribStack.glPopClientAttrib();
            assertEquals(0, GLSMQueries.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH));

            final int max = GLSMQueries.glGetInteger(GL11.GL_MAX_CLIENT_ATTRIB_STACK_DEPTH);
            for (int i = 0; i < max + 2; i++) {
                GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            }
            assertEquals(max, GLSMQueries.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH));
        } finally {
            drainClientAttribStack();
        }
    }

    @Test
    void textureStackDepthIsPerActiveUnit() {
        try {
            GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            assertEquals(1, GLSMQueries.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH));
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            GLSMMatrices.glPushMatrix();
            assertEquals(2, GLSMQueries.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH));
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            assertEquals(1, GLSMQueries.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH));
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            assertEquals(2, GLSMQueries.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH));
            GLSMMatrices.glPopMatrix();
            assertEquals(1, GLSMQueries.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH));
        } finally {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
            popToBase(GL11.GL_TEXTURE_STACK_DEPTH);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    static Stream<Arguments> matrixStacks() {
        return Stream.of(
            Arguments.of("modelview", GL11.GL_MODELVIEW, GL11.GL_MODELVIEW_STACK_DEPTH, GL11.GL_MAX_MODELVIEW_STACK_DEPTH),
            Arguments.of("projection", GL11.GL_PROJECTION, GL11.GL_PROJECTION_STACK_DEPTH, GL11.GL_MAX_PROJECTION_STACK_DEPTH),
            Arguments.of("texture", GL11.GL_TEXTURE, GL11.GL_TEXTURE_STACK_DEPTH, GL11.GL_MAX_TEXTURE_STACK_DEPTH));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrixStacks")
    void maxMatrixStackDepthIsExactlyReachable(String name, int mode, int depthPname, int maxPname) {
        try {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMMatrices.glMatrixMode(mode);
            assertEquals(1, GLSMQueries.glGetInteger(depthPname));
            assertEquals(GLSMQueries.glGetInteger(maxPname), pushToLimit(depthPname, maxPname));
        } finally {
            GLSMMatrices.glMatrixMode(mode);
            popToBase(depthPname);
            GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    @Test
    void constantQueriesReportEmulatedLimits() {
        assertEquals(Math.min(VertexKey.MAX_UNITS, GLStateManager.MAX_TEXTURE_UNITS), GLSMQueries.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS));
        assertEquals(8, GLSMQueries.glGetInteger(GL11.GL_MAX_LIGHTS));
    }

    @Test
    void stencilAndDepthBitsMatchDefaultFramebuffer() {
        final GLContextState glCtx = GLStateManager.ctx();
        final int savedDraw = glCtx.drawFramebuffer;
        final int savedRead = glCtx.readFramebuffer;
        try {
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            assertEquals(
                GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL11.GL_STENCIL, GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE),
                GLSMQueries.glGetInteger(GL11.GL_STENCIL_BITS));
            assertEquals(
                GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL11.GL_DEPTH, GL30.GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE),
                GLSMQueries.glGetInteger(GL11.GL_DEPTH_BITS));
        } finally {
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, savedDraw);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, savedRead);
        }
    }

    @Test
    void stencilAndDepthBitsFollowBoundDrawFramebuffer() {
        final GLContextState glCtx = GLStateManager.ctx();
        final int savedDraw = glCtx.drawFramebuffer;
        final int savedRead = glCtx.readFramebuffer;
        final int fbo = GLSMFramebuffer.glGenFramebuffers();
        final int depthOnly = GLSMFramebuffer.glGenRenderbuffers();
        final int depthStencil = GLSMFramebuffer.glGenRenderbuffers();
        try {
            GLSMFramebuffer.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthOnly);
            GLSMFramebuffer.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, 16, 16);
            GLSMFramebuffer.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthStencil);
            GLSMFramebuffer.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, 16, 16);
            GLSMFramebuffer.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);

            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            assertEquals(0, GLSMQueries.glGetInteger(GL11.GL_STENCIL_BITS));
            assertEquals(0, GLSMQueries.glGetInteger(GL11.GL_DEPTH_BITS));

            GLSMFramebuffer.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthOnly);
            assertEquals(0, GLSMQueries.glGetInteger(GL11.GL_STENCIL_BITS));
            final int depthOnlyBits = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE);
            assertTrue(depthOnlyBits >= 24, "driver depth size " + depthOnlyBits);
            assertEquals(depthOnlyBits, GLSMQueries.glGetInteger(GL11.GL_DEPTH_BITS));

            GLSMFramebuffer.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthStencil);
            GLSMFramebuffer.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, depthStencil);
            assertEquals(8, GLSMQueries.glGetInteger(GL11.GL_STENCIL_BITS));
            assertEquals(24, GLSMQueries.glGetInteger(GL11.GL_DEPTH_BITS));
        } finally {
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, savedDraw);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, savedRead);
            GLSMFramebuffer.glDeleteFramebuffers(fbo);
            GLSMFramebuffer.glDeleteRenderbuffers(depthOnly);
            GLSMFramebuffer.glDeleteRenderbuffers(depthStencil);
        }
    }

    @Test
    void attribDepthUnchangedWhileCompilingDisplayList() {
        final int base = GLSMQueries.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH);
        final int list = GLSMDraws.glGenLists(1);
        try {
            GLSMDraws.glNewList(list, GL11.GL_COMPILE);
            GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT);
            assertEquals(base, GLSMQueries.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH));
            GLSMAttribStack.glPopAttrib();
            assertEquals(base, GLSMQueries.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH));
            GLSMDraws.glEndList();
            assertEquals(base, GLSMQueries.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH));
        } finally {
            if (DisplayListManager.isRecording()) {
                GLSMDraws.glEndList();
            }
            GLSMDraws.glDeleteLists(list, 1);
        }
    }

    @Test
    void popAttribOnEmptyStackIsIgnored() {
        popAttribTo(0);
        assertEquals(0, GLSMAttribStack.getAttribDepth());
        assertDoesNotThrow(GLSMAttribStack::glPopAttrib);
        assertEquals(0, GLSMQueries.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH));
        assertDoesNotThrow(GLSMAttribStack::glPopAttrib);
        assertEquals(0, GLSMAttribStack.getAttribDepth());
    }

    @Test
    void pixelStoreBitRestoresPixelStoreAndPboBindings() {
        final GLContextState glCtx = GLStateManager.ctx();
        final int unpackBuffer = GLSMBuffers.glGenBuffers();
        final int packBuffer = GLSMBuffers.glGenBuffers();
        try {
            // Pack and unpack start from different values, so a pop that crosses them is caught
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 2);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 8);
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 3);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 5);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            final PixelStoreState unpackBefore = glCtx.pixelUnpackState;
            final PixelStoreState packBefore = glCtx.pixelPackState;

            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 6);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 7);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 7);
            GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            assertEquals(1, glCtx.pixelUnpackState.alignment());
            assertEquals(4, glCtx.pixelPackState.alignment());
            assertEquals(6, glCtx.pixelUnpackState.skipRows());
            assertEquals(7, glCtx.pixelPackState.skipRows());
            assertEquals(7, glCtx.pixelPackState.rowLength());
            assertEquals(4, GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT));
            assertEquals(6, GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS));
            assertEquals(7, GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS));
            GLSMAttribStack.glPopClientAttrib();

            assertSame(unpackBefore, glCtx.pixelUnpackState);
            assertSame(packBefore, glCtx.pixelPackState);
            assertEquals(2, GLSMQueries.glGetInteger(GL11.GL_UNPACK_ALIGNMENT));
            assertEquals(8, GLSMQueries.glGetInteger(GL11.GL_PACK_ALIGNMENT));
            assertEquals(0, GLSMQueries.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING));
            assertEquals(0, GLSMQueries.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING));
            assertEquals(2, GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT));
            assertEquals(8, GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT));
            assertEquals(3, GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS));
            assertEquals(5, GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS));
            assertEquals(0, GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH));
            assertEquals(0, GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING));
            assertEquals(0, GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING));
        } finally {
            drainClientAttribStack();
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GLSMBuffers.glDeleteBuffers(unpackBuffer);
            GLSMBuffers.glDeleteBuffers(packBuffer);
        }
    }

    @Test
    void pixelStoreIsNotRestoredWithoutPixelStoreBit() {
        final GLContextState glCtx = GLStateManager.ctx();
        final int unpackBuffer = GLSMBuffers.glGenBuffers();
        final int packBuffer = GLSMBuffers.glGenBuffers();
        try {
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);

            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 2);
            GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            GLSMAttribStack.glPopClientAttrib();

            assertEquals(1, glCtx.pixelUnpackState.alignment());
            assertEquals(2, glCtx.pixelPackState.alignment());
            assertEquals(unpackBuffer, GLSMQueries.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING));
            assertEquals(packBuffer, GLSMQueries.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING));
            assertEquals(1, GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT));
            assertEquals(2, GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT));
            assertEquals(unpackBuffer, GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING));
            assertEquals(packBuffer, GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING));
        } finally {
            drainClientAttribStack();
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GLSMBuffers.glDeleteBuffers(unpackBuffer);
            GLSMBuffers.glDeleteBuffers(packBuffer);
        }
    }

    @Test
    void pixelStoreWithReturnsSameInstanceForUnchangedValue() {
        final GLContextState glCtx = GLStateManager.ctx();
        try {
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 9);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 11);
            final PixelStoreState unpack = glCtx.pixelUnpackState;
            final PixelStoreState pack = glCtx.pixelPackState;

            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 9);
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpack.alignment());
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, 0);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 11);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_ALIGNMENT, pack.alignment());
            GLSMTextures.glPixelStorei(GL11.GL_PACK_LSB_FIRST, 0);

            assertSame(unpack, glCtx.pixelUnpackState);
            assertSame(pack, glCtx.pixelPackState);
            assertSame(unpack, unpack.with(GL11.GL_UNPACK_ROW_LENGTH, 9));
            assertSame(pack, pack.with(GL11.GL_PACK_ROW_LENGTH, 11));
        } finally {
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
        }
    }

    @Test
    void intLightGettersConvertLikeMesa() {
        final FloatBuffer savedDiffuse = BufferUtils.createFloatBuffer(16);
        final FloatBuffer savedPosition = BufferUtils.createFloatBuffer(16);
        final FloatBuffer savedDirection = BufferUtils.createFloatBuffer(16);
        final FloatBuffer savedCutoff = BufferUtils.createFloatBuffer(16);
        final FloatBuffer savedAttenuation = BufferUtils.createFloatBuffer(16);
        GLSMLighting.glGetLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, savedDiffuse);
        GLSMLighting.glGetLight(GL11.GL_LIGHT1, GL11.GL_POSITION, savedPosition);
        GLSMLighting.glGetLight(GL11.GL_LIGHT1, GL11.GL_SPOT_DIRECTION, savedDirection);
        GLSMLighting.glGetLight(GL11.GL_LIGHT1, GL11.GL_SPOT_CUTOFF, savedCutoff);
        GLSMLighting.glGetLight(GL11.GL_LIGHT1, GL11.GL_LINEAR_ATTENUATION, savedAttenuation);
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glPushMatrix();
        try {
            GLSMMatrices.glLoadIdentity();
            final FloatBuffer values = BufferUtils.createFloatBuffer(4);
            values.put(0, 0.25f).put(1, 0.5f).put(2, 1.0f).put(3, -0.5f);
            GLSMLighting.glLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, values);
            values.put(0, 3.9f).put(1, -2.7f).put(2, 0.5f).put(3, 1.0f);
            GLSMLighting.glLight(GL11.GL_LIGHT1, GL11.GL_POSITION, values);
            values.put(0, 2.5f).put(1, -1.5f).put(2, -3.9f).put(3, 0.0f);
            GLSMLighting.glLight(GL11.GL_LIGHT1, GL11.GL_SPOT_DIRECTION, values);
            GLSMLighting.glLightf(GL11.GL_LIGHT1, GL11.GL_SPOT_CUTOFF, 45.75f);
            GLSMLighting.glLightf(GL11.GL_LIGHT1, GL11.GL_LINEAR_ATTENUATION, 2.5f);

            ints.position(2);
            GLSMLighting.glGetLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, ints);
            assertEquals(2, ints.position());
            assertEquals(536870911, ints.get(2));
            assertEquals(1073741823, ints.get(3));
            assertEquals(2147483647, ints.get(4));
            assertEquals(-1073741823, ints.get(5));
            ints.position(0);

            GLSMLighting.glGetLight(GL11.GL_LIGHT1, GL11.GL_POSITION, ints);
            assertEquals(3, ints.get(0));
            assertEquals(-2, ints.get(1));
            assertEquals(0, ints.get(2));
            assertEquals(1, ints.get(3));

            ints.put(3, -777);
            GLSMLighting.glGetLight(GL11.GL_LIGHT1, GL11.GL_SPOT_DIRECTION, ints);
            assertEquals(2, ints.get(0));
            assertEquals(-1, ints.get(1));
            assertEquals(-3, ints.get(2));
            assertEquals(-777, ints.get(3));

            ints.put(1, -777);
            GLSMLighting.glGetLight(GL11.GL_LIGHT1, GL11.GL_SPOT_CUTOFF, ints);
            assertEquals(45, ints.get(0));
            assertEquals(-777, ints.get(1));
            GLSMLighting.glGetLight(GL11.GL_LIGHT1, GL11.GL_LINEAR_ATTENUATION, ints);
            assertEquals(2, ints.get(0));

            ints.put(0, -777);
            GLSMLighting.glGetLight(GL11.GL_LIGHT1, GL11.GL_EMISSION, ints);
            assertEquals(-777, ints.get(0));
            GLSMLighting.glGetLight(GL11.GL_LIGHT1, GL11.GL_SHININESS, ints);
            assertEquals(-777, ints.get(0));
        } finally {
            GLSMMatrices.glLoadIdentity();
            savedDiffuse.limit(4);
            GLSMLighting.glLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, savedDiffuse);
            savedPosition.limit(4);
            GLSMLighting.glLight(GL11.GL_LIGHT1, GL11.GL_POSITION, savedPosition);
            savedDirection.limit(4);
            GLSMLighting.glLight(GL11.GL_LIGHT1, GL11.GL_SPOT_DIRECTION, savedDirection);
            GLSMLighting.glLightf(GL11.GL_LIGHT1, GL11.GL_SPOT_CUTOFF, savedCutoff.get(0));
            GLSMLighting.glLightf(GL11.GL_LIGHT1, GL11.GL_LINEAR_ATTENUATION, savedAttenuation.get(0));
            GLSMMatrices.glPopMatrix();
        }
    }

    @Test
    void intMaterialGettersConvertLikeMesa() {
        final FloatBuffer savedSpecular = BufferUtils.createFloatBuffer(16);
        final FloatBuffer savedShininess = BufferUtils.createFloatBuffer(16);
        final FloatBuffer savedIndexes = BufferUtils.createFloatBuffer(16);
        GLSMLighting.glGetMaterial(GL11.GL_FRONT, GL11.GL_SPECULAR, savedSpecular);
        GLSMLighting.glGetMaterial(GL11.GL_FRONT, GL11.GL_SHININESS, savedShininess);
        GLSMLighting.glGetMaterial(GL11.GL_BACK, GL11.GL_COLOR_INDEXES, savedIndexes);
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        try {
            final FloatBuffer values = BufferUtils.createFloatBuffer(4);
            values.put(0, 0.5f).put(1, 0.75f).put(2, 1.0f).put(3, 0.0f);
            GLSMLighting.glMaterial(GL11.GL_FRONT, GL11.GL_SPECULAR, values);
            GLSMLighting.glMaterialf(GL11.GL_FRONT, GL11.GL_SHININESS, 12.6f);
            values.put(0, 2.5f).put(1, -2.5f).put(2, 7.4f).put(3, 0.0f);
            GLSMLighting.glMaterial(GL11.GL_BACK, GL11.GL_COLOR_INDEXES, values);

            ints.position(1);
            GLSMLighting.glGetMaterial(GL11.GL_FRONT, GL11.GL_SPECULAR, ints);
            assertEquals(1, ints.position());
            assertEquals(1073741823, ints.get(1));
            assertEquals(1610612735, ints.get(2));
            assertEquals(2147483647, ints.get(3));
            assertEquals(0, ints.get(4));
            ints.position(0);

            ints.put(1, -777);
            GLSMLighting.glGetMaterial(GL11.GL_FRONT, GL11.GL_SHININESS, ints);
            assertEquals(13, ints.get(0));
            assertEquals(-777, ints.get(1));

            ints.put(3, -777);
            GLSMLighting.glGetMaterial(GL11.GL_BACK, GL11.GL_COLOR_INDEXES, ints);
            assertEquals(3, ints.get(0));
            assertEquals(-3, ints.get(1));
            assertEquals(7, ints.get(2));
            assertEquals(-777, ints.get(3));

            ints.put(0, -777);
            GLSMLighting.glGetMaterial(GL11.GL_FRONT, GL11.GL_POSITION, ints);
            assertEquals(-777, ints.get(0));
            GLSMLighting.glGetMaterial(GL11.GL_FRONT, GL11.GL_SPOT_CUTOFF, ints);
            assertEquals(-777, ints.get(0));
        } finally {
            savedSpecular.limit(4);
            GLSMLighting.glMaterial(GL11.GL_FRONT, GL11.GL_SPECULAR, savedSpecular);
            GLSMLighting.glMaterialf(GL11.GL_FRONT, GL11.GL_SHININESS, savedShininess.get(0));
            savedIndexes.limit(4);
            GLSMLighting.glMaterial(GL11.GL_BACK, GL11.GL_COLOR_INDEXES, savedIndexes);
        }
    }

    @Test
    void currentTextureCoordsFollowActiveUnit() {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(16);
        final float[][] saved = new float[4][4];
        try {
            for (int unit = 0; unit < 4; unit++) {
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                GLSMQueries.glGetFloat(GL11.GL_CURRENT_TEXTURE_COORDS, buf);
                for (int i = 0; i < 4; i++) {
                    saved[unit][i] = buf.get(i);
                }
            }
            GLSMCurrentAttribs.glMultiTexCoord2f(GL13.GL_TEXTURE0, 0.25f, 0.5f);
            GLSMCurrentAttribs.glMultiTexCoord2f(GL13.GL_TEXTURE1, 240.0f, 16.0f);
            GLSMCurrentAttribs.glMultiTexCoord2f(GL13.GL_TEXTURE2, 0.75f, 0.125f);
            GLSMCurrentAttribs.glMultiTexCoord2f(GL13.GL_TEXTURE3, 0.375f, 0.625f);

            final float[][] expected = { { 0.25f, 0.5f }, { 240.0f, 16.0f }, { 0.75f, 0.125f }, { 0.375f, 0.625f } };
            for (int unit = 0; unit < 4; unit++) {
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                GLSMQueries.glGetFloat(GL11.GL_CURRENT_TEXTURE_COORDS, buf);
                assertEquals(expected[unit][0], buf.get(0), "s of unit " + unit);
                assertEquals(expected[unit][1], buf.get(1), "t of unit " + unit);
                assertEquals(0.0f, buf.get(2), "r of unit " + unit);
                assertEquals(1.0f, buf.get(3), "q of unit " + unit);
            }
        } finally {
            for (int unit = 0; unit < 4; unit++) {
                GLSMCurrentAttribs.glMultiTexCoord2f(GL13.GL_TEXTURE0 + unit, saved[unit][0], saved[unit][1]);
            }
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        }
    }

    @Test
    void currentTextureCoordsOfUntrackedUnitAreInitialValue() {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(16);
        final int unit = GLStateManager.ctx().ffp.currentTexCoords.length + 1;
        try {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMCurrentAttribs.glMultiTexCoord2f(GL13.GL_TEXTURE0, 0.25f, 0.5f);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            for (int i = 0; i < 4; i++) {
                buf.put(i, -7.0f);
            }
            GLSMQueries.glGetFloat(GL11.GL_CURRENT_TEXTURE_COORDS, buf);
            assertEquals(0.0f, buf.get(0));
            assertEquals(0.0f, buf.get(1));
            assertEquals(0.0f, buf.get(2));
            assertEquals(1.0f, buf.get(3));
        } finally {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMCurrentAttribs.glMultiTexCoord2f(GL13.GL_TEXTURE0, 0.0f, 0.0f);
        }
    }

    @Test
    void legacyQueriesNeverReachTheDriver() {
        final int base = GLSMAttribStack.getAttribDepth();
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        final FloatBuffer floats = BufferUtils.createFloatBuffer(16);
        final DoubleBuffer doubles = BufferUtils.createDoubleBuffer(16);
        final ByteBuffer bytes = BufferUtils.createByteBuffer(16);
        final int[] intArray = new int[16];
        final float[] floatArray = new float[16];
        final double[] doubleArray = new double[16];
        try {
            GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE1);
            assumeDriverRejectsLegacyPnames();
            for (final int pname : LEGACY_PNAMES) {
                drainErrors();
                final int scalar = GLSMQueries.glGetInteger(pname);
                GLSMQueries.glGetFloat(pname);
                GLSMQueries.glGetBoolean(pname);
                GLSMEnable.glIsEnabled(pname);
                ints.clear();
                ints.put(0, -12345);
                GLSMQueries.glGetInteger(pname, ints);
                floats.clear();
                GLSMQueries.glGetFloat(pname, floats);
                doubles.clear();
                GLSMQueries.glGetDouble(pname, doubles);
                bytes.clear();
                GLSMQueries.glGetBoolean(pname, bytes);
                intArray[0] = -12345;
                GLSMQueries.glGetInteger(pname, intArray);
                GLSMQueries.glGetFloat(pname, floatArray);
                GLSMQueries.glGetDouble(pname, doubleArray);
                if (LegacyQueries.count(LegacyQueries.desc(pname)) == 1) {
                    assertEquals(scalar, ints.get(0), "IntBuffer overload for " + hex(pname));
                    assertEquals(scalar, intArray[0], "int[] overload for " + hex(pname));
                }
                assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "driver error after querying " + hex(pname));
            }
        } finally {
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
            drainClientAttribStack();
            popAttribTo(base);
        }
    }

    @Test
    void typedEntryPointsAgreeUnderMesaConversions() {
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        final FloatBuffer floats = BufferUtils.createFloatBuffer(16);
        final DoubleBuffer doubles = BufferUtils.createDoubleBuffer(16);
        final FloatBuffer savedColor = BufferUtils.createFloatBuffer(16);
        final FloatBuffer savedNormal = BufferUtils.createFloatBuffer(16);
        GLSMQueries.glGetFloat(GL11.GL_CURRENT_COLOR, savedColor);
        GLSMQueries.glGetFloat(GL11.GL_CURRENT_NORMAL, savedNormal);
        final int savedFunc = GLSMQueries.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        final float savedRef = GLSMQueries.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        final float savedStart = GLSMQueries.glGetFloat(GL11.GL_FOG_START);
        final float savedEnd = GLSMQueries.glGetFloat(GL11.GL_FOG_END);
        final float savedDensity = GLSMQueries.glGetFloat(GL11.GL_FOG_DENSITY);
        final boolean savedLighting = GLSMEnable.glIsEnabled(GL11.GL_LIGHTING);
        final boolean savedFog = GLSMEnable.glIsEnabled(GL11.GL_FOG);
        final int savedShade = GLSMQueries.glGetInteger(GL11.GL_SHADE_MODEL);
        final int savedHint = GLSMQueries.glGetInteger(GL11.GL_FOG_HINT);
        final float savedMinSize = GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MIN);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glPushMatrix();
        try {
            GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.5f);
            GLSMFog.glFogf(GL11.GL_FOG_START, -1.5f);
            GLSMFog.glFogf(GL11.GL_FOG_END, 2.5f);
            GLSMFog.glFogf(GL11.GL_FOG_DENSITY, 0.25f);
            GLSMCurrentAttribs.glColor4f(0.25f, 0.5f, 0.75f, 1f);
            GLSMCurrentAttribs.glNormal3f(0f, -0.5f, 1f);
            GLSMEnable.glEnable(GL11.GL_LIGHTING);
            GLSMEnable.glEnable(GL11.GL_FOG);
            GLSMRasterizer.glShadeModel(GL11.GL_FLAT);
            GLSMRasterizer.glHint(GL11.GL_FOG_HINT, GL11.GL_NICEST);
            GLSMRasterizer.glPointParameterf(GL14.GL_POINT_SIZE_MIN, 2.5f);
            GLSMMatrices.glTranslatef(1.5f, -2.5f, 3f);
            for (final int pname : LEGACY_PNAMES) {
                final int desc = LegacyQueries.desc(pname);
                final int type = LegacyQueries.type(desc);
                final int count = LegacyQueries.count(desc);
                final String name = hex(pname);
                if (count == 1) {
                    if (type == LegacyQueries.BOOL) {
                        final boolean b = GLSMQueries.glGetBoolean(pname);
                        assertEquals(b ? 1 : 0, GLSMQueries.glGetInteger(pname), "int of bool " + name);
                        assertEquals(b ? 1.0f : 0.0f, GLSMQueries.glGetFloat(pname), "float of bool " + name);
                    } else if (type == LegacyQueries.INT) {
                        final int i = GLSMQueries.glGetInteger(pname);
                        assertEquals((float) i, GLSMQueries.glGetFloat(pname), "float of int " + name);
                        assertEquals(i != 0, GLSMQueries.glGetBoolean(pname), "bool of int " + name);
                    } else {
                        final float f = GLSMQueries.glGetFloat(pname);
                                                assertEquals(LegacyQueries.toInt(type, f), GLSMQueries.glGetInteger(pname), "int of float " + name);
                        assertEquals(f != 0.0f, GLSMQueries.glGetBoolean(pname), "bool of float " + name);
                    }
                } else {
                    floats.clear();
                    GLSMQueries.glGetFloat(pname, floats);
                    ints.clear();
                    GLSMQueries.glGetInteger(pname, ints);
                    doubles.clear();
                    GLSMQueries.glGetDouble(pname, doubles);
                    for (int i = 0; i < count; i++) {
                        final float f = floats.get(i);
                                                assertEquals(LegacyQueries.toInt(type, f), ints.get(i), "int element " + i + " of " + name);
                        assertEquals((double) f, doubles.get(i), "double element " + i + " of " + name);
                    }
                    assertEquals(floats.get(0), GLSMQueries.glGetFloat(pname), "scalar float of " + name);
                }
            }
        } finally {
            GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
            GLSMMatrices.glPopMatrix();
            GLSMRasterizer.glPointParameterf(GL14.GL_POINT_SIZE_MIN, savedMinSize);
            GLSMRasterizer.glHint(GL11.GL_FOG_HINT, savedHint);
            GLSMRasterizer.glShadeModel(savedShade);
            setEnabled(GL11.GL_FOG, savedFog);
            setEnabled(GL11.GL_LIGHTING, savedLighting);
            GLSMCurrentAttribs.glNormal3f(savedNormal.get(0), savedNormal.get(1), savedNormal.get(2));
            GLSMCurrentAttribs.glColor4f(savedColor.get(0), savedColor.get(1), savedColor.get(2), savedColor.get(3));
            GLSMFog.glFogf(GL11.GL_FOG_DENSITY, savedDensity);
            GLSMFog.glFogf(GL11.GL_FOG_END, savedEnd);
            GLSMFog.glFogf(GL11.GL_FOG_START, savedStart);
            GLSMDepthStencilAlpha.glAlphaFunc(savedFunc, savedRef);
        }
    }

    private static float driverMaxPointSize() {
        final FloatBuffer range = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_POINT_SIZE_RANGE, range);
        final float smooth = range.get(1);
        // A rejected query leaves the buffer alone, so the smooth maximum stands in for the missing aliased range
        GL11.glGetFloat(GL12.GL_ALIASED_POINT_SIZE_RANGE, range);
        drainErrors();
        return Math.max(smooth, range.get(1));
    }

    @Test
    void edgeFlagIsCompiledIntoDisplayLists() {
        final int list = GLSMDraws.glGenLists(1);
        try {
            GLSMCurrentAttribs.glEdgeFlag(true);
            GLSMDraws.glNewList(list, GL11.GL_COMPILE);
            GLSMCurrentAttribs.glEdgeFlag(false);
            GLSMDraws.glEndList();
            assertTrue(GLSMQueries.glGetBoolean(GL11.GL_EDGE_FLAG), "compiling must not change the live edge flag");
            GLSMDraws.glCallList(list);
            assertFalse(GLSMQueries.glGetBoolean(GL11.GL_EDGE_FLAG));
        } finally {
            GLSMCurrentAttribs.glEdgeFlag(true);
            GLSMDraws.glDeleteLists(list, 1);
        }
    }

    @Test
    void textureBitRestoresPerUnitTextureEnables() {
        final int base = GLSMAttribStack.getAttribDepth();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        final boolean cube = GLSMEnable.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP);
        final boolean texture2d = GLSMEnable.glIsEnabled(GL11.GL_TEXTURE_2D);
        try {
            GLSMAttribStack.glPushAttrib(GL11.GL_TEXTURE_BIT);
            setEnabled(GL13.GL_TEXTURE_CUBE_MAP, !cube);
            setEnabled(GL11.GL_TEXTURE_2D, !texture2d);
            GLSMAttribStack.glPopAttrib();
            assertEquals(cube, GLSMEnable.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP));
            assertEquals(texture2d, GLSMEnable.glIsEnabled(GL11.GL_TEXTURE_2D));
        } finally {
            popAttribTo(base);
            setEnabled(GL13.GL_TEXTURE_CUBE_MAP, cube);
            setEnabled(GL11.GL_TEXTURE_2D, texture2d);
        }
    }

    @Test
    void crossTypeQueriesConvert() {
        final FloatBuffer savedColor = BufferUtils.createFloatBuffer(16);
        GLSMQueries.glGetFloat(GL11.GL_CURRENT_COLOR, savedColor);
        final int savedFunc = GLSMQueries.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        final float savedRef = GLSMQueries.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        final float savedStart = GLSMQueries.glGetFloat(GL11.GL_FOG_START);
        final boolean savedLighting = GLSMEnable.glIsEnabled(GL11.GL_LIGHTING);
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glPushMatrix();
        try {
            GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.5f);
            assertEquals(1073741823, GLSMQueries.glGetInteger(GL11.GL_ALPHA_TEST_REF));

            assertEquals((float) GLSMQueries.glGetInteger(GL11.GL_FOG_MODE), GLSMQueries.glGetFloat(GL11.GL_FOG_MODE));

            GLSMFog.glFogf(GL11.GL_FOG_START, -1.5f);
            assertEquals(-2, GLSMQueries.glGetInteger(GL11.GL_FOG_START));

            GLSMEnable.glEnable(GL11.GL_LIGHTING);
            assertEquals(1, GLSMQueries.glGetInteger(GL11.GL_LIGHTING));
            GLSMEnable.glDisable(GL11.GL_LIGHTING);
            assertEquals(0, GLSMQueries.glGetInteger(GL11.GL_LIGHTING));

            GLSMCurrentAttribs.glColor4f(1f, 0.5f, 0f, 1f);
            ints.clear();
            GLSMQueries.glGetInteger(GL11.GL_CURRENT_COLOR, ints);
            assertEquals(2147483647, ints.get(0));
            assertEquals(1073741823, ints.get(1));
            assertEquals(0, ints.get(2));
            assertEquals(2147483647, ints.get(3));

            GLSMMatrices.glLoadIdentity();
            GLSMQueries.glGetInteger(GL11.GL_MODELVIEW_MATRIX, ints);
            assertEquals(2147483647, ints.get(0), "matrix elements scale like normalized floats");
            assertEquals(0, ints.get(1));
            assertEquals(2147483647, ints.get(15));
            assertEquals(2147483647, GLSMQueries.glGetInteger(GL11.GL_MODELVIEW_MATRIX));
            assertEquals(1.0f, GLSMQueries.glGetFloat(GL11.GL_MODELVIEW_MATRIX));
        } finally {
            GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
            GLSMMatrices.glPopMatrix();
            GLSMFog.glFogf(GL11.GL_FOG_START, savedStart);
            GLSMCurrentAttribs.glColor4f(savedColor.get(0), savedColor.get(1), savedColor.get(2), savedColor.get(3));
            GLSMDepthStencilAlpha.glAlphaFunc(savedFunc, savedRef);
            setEnabled(GL11.GL_LIGHTING, savedLighting);
        }
    }

    @Test
    void conversionHelpersMatchMesa() {
        assertEquals(1, LegacyQueries.lround(0.5f));
        assertEquals(-1, LegacyQueries.lround(-0.5f));
        assertEquals(2, LegacyQueries.lround(2.4f));
        assertEquals(-3, LegacyQueries.lround(-2.6f));
        assertEquals(Integer.MAX_VALUE, LegacyQueries.floatToInt(1f));
        assertEquals(0, LegacyQueries.floatToInt(0f));
        assertEquals(-Integer.MAX_VALUE, LegacyQueries.floatToInt(-1f));
    }

    @Test
    void queriesWriteAtBufferPosition() {
        final IntBuffer ints = BufferUtils.createIntBuffer(32);
        final FloatBuffer floats = BufferUtils.createFloatBuffer(32);
        final DoubleBuffer doubles = BufferUtils.createDoubleBuffer(32);
        final FloatBuffer reference = BufferUtils.createFloatBuffer(32);
        final FloatBuffer savedColor = BufferUtils.createFloatBuffer(16);
        GLSMQueries.glGetFloat(GL11.GL_CURRENT_COLOR, savedColor);
        final float savedStart = GLSMQueries.glGetFloat(GL11.GL_FOG_START);
        try {
            for (int i = 0; i < 32; i++) {
                ints.put(i, -777);
                floats.put(i, -777f);
                doubles.put(i, -777.0);
            }

            ints.position(4);
            GLSMQueries.glGetInteger(GL11.GL_MATRIX_MODE, ints);
            assertEquals(GLSMQueries.glGetInteger(GL11.GL_MATRIX_MODE), ints.get(4));
            assertEquals(-777, ints.get(0));

            floats.position(4);
            GLSMQueries.glGetFloat(GL11.GL_MODELVIEW_MATRIX, floats);
            reference.clear();
            GLSMQueries.glGetFloat(GL11.GL_MODELVIEW_MATRIX, reference);
            for (int i = 0; i < 16; i++) {
                assertEquals(reference.get(i), floats.get(4 + i), "modelview element " + i);
            }
            assertEquals(-777f, floats.get(0));

            for (int i = 0; i < 32; i++) {
                ints.put(i, -777);
            }
            GLSMCurrentAttribs.glColor4f(0.25f, 0.5f, 0.75f, 1f);
            ints.position(4);
            GLSMQueries.glGetInteger(GL11.GL_CURRENT_COLOR, ints);
            assertEquals(536870911, ints.get(4));
            assertEquals(1073741823, ints.get(5));
            assertEquals(1610612735, ints.get(6));
            assertEquals(2147483647, ints.get(7));
            assertEquals(-777, ints.get(0));

            GLSMFog.glFogf(GL11.GL_FOG_START, 3.5f);
            doubles.position(4);
            GLSMQueries.glGetDouble(GL11.GL_FOG_START, doubles);
            assertEquals(3.5, doubles.get(4));
            assertEquals(-777.0, doubles.get(0));

            for (int i = 0; i < 32; i++) {
                floats.put(i, -777f);
            }
            floats.position(4);
            GLSMLighting.glGetMaterial(GL11.GL_FRONT, GL11.GL_DIFFUSE, floats);
            reference.clear();
            GLSMLighting.glGetMaterial(GL11.GL_FRONT, GL11.GL_DIFFUSE, reference);
            for (int i = 0; i < 4; i++) {
                assertEquals(reference.get(i), floats.get(4 + i), "diffuse element " + i);
            }
            assertEquals(-777f, floats.get(0));
        } finally {
            GLSMFog.glFogf(GL11.GL_FOG_START, savedStart);
            GLSMCurrentAttribs.glColor4f(savedColor.get(0), savedColor.get(1), savedColor.get(2), savedColor.get(3));
        }
    }

    private static boolean isDriverHint(int target) {
        return target == GL11.GL_LINE_SMOOTH_HINT || target == GL11.GL_POLYGON_SMOOTH_HINT
            || target == GL13.GL_TEXTURE_COMPRESSION_HINT || target == GL20.GL_FRAGMENT_SHADER_DERIVATIVE_HINT;
    }

    @Test
    void hintsRoundTripAndFollowHintBit() {
        final int base = GLSMAttribStack.getAttribDepth();
        final int[] saved = new int[HINT_TARGETS.length];
        for (int i = 0; i < HINT_TARGETS.length; i++) {
            saved[i] = GLSMQueries.glGetInteger(HINT_TARGETS[i]);
        }
        try {
            for (final int target : HINT_TARGETS) {
                final String name = hex(target);
                assertEquals(GL11.GL_DONT_CARE, GLSMQueries.glGetInteger(target), "default " + name);
                GLSMRasterizer.glHint(target, GL11.GL_NICEST);
                assertEquals(GL11.GL_NICEST, GLSMQueries.glGetInteger(target), "nicest " + name);
                if (isDriverHint(target)) assertEquals(GL11.GL_NICEST, GL11.glGetInteger(target), "driver nicest " + name);
                GLSMRasterizer.glHint(target, 12345);
                assertEquals(GL11.GL_NICEST, GLSMQueries.glGetInteger(target), "invalid mode " + name);
                if (isDriverHint(target)) assertEquals(GL11.GL_NICEST, GL11.glGetInteger(target), "driver invalid mode " + name);
            }
            GLSMAttribStack.glPushAttrib(GL11.GL_HINT_BIT);
            for (final int target : HINT_TARGETS) {
                GLSMRasterizer.glHint(target, GL11.GL_FASTEST);
                assertEquals(GL11.GL_FASTEST, GLSMQueries.glGetInteger(target), "fastest " + hex(target));
                if (isDriverHint(target)) assertEquals(GL11.GL_FASTEST, GL11.glGetInteger(target), "driver fastest " + hex(target));
            }
            GLSMAttribStack.glPopAttrib();
            for (final int target : HINT_TARGETS) {
                assertEquals(GL11.GL_NICEST, GLSMQueries.glGetInteger(target), "restored " + hex(target));
                if (isDriverHint(target)) assertEquals(GL11.GL_NICEST, GL11.glGetInteger(target), "driver restored " + hex(target));
            }
            assertEquals(GL11.GL_RENDER, GLSMQueries.glGetInteger(GL11.GL_RENDER_MODE));
        } finally {
            popAttribTo(base);
            for (int i = 0; i < HINT_TARGETS.length; i++) {
                GLSMRasterizer.glHint(HINT_TARGETS[i], saved[i]);
            }
        }
    }

    @Test
    void pointParametersRoundTripAndFollowPointBit() {
        final int base = GLSMAttribStack.getAttribDepth();
        final FloatBuffer attenuation = BufferUtils.createFloatBuffer(16);
        final FloatBuffer savedAttenuation = BufferUtils.createFloatBuffer(16);
        final float savedMin = GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MIN);
        final float savedMax = GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MAX);
        final float savedFade = GLSMQueries.glGetFloat(GL14.GL_POINT_FADE_THRESHOLD_SIZE);
        final int savedOrigin = GLSMQueries.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN);
        GLSMQueries.glGetFloat(GL14.GL_POINT_DISTANCE_ATTENUATION, savedAttenuation);
        GLSMAttribStack.glPushAttrib(GL11.GL_POINT_BIT);
        try {
            assertEquals(0f, savedMin);
            assertEquals(driverMaxPointSize(), savedMax);
            assertEquals(1f, savedFade);
            assertEquals(1f, savedAttenuation.get(0));
            assertEquals(0f, savedAttenuation.get(1));
            assertEquals(0f, savedAttenuation.get(2));
            assertEquals(GL20.GL_UPPER_LEFT, savedOrigin);

            GLSMRasterizer.glPointParameterf(GL14.GL_POINT_SIZE_MIN, 0.5f);
            GLSMRasterizer.glPointParameterf(GL14.GL_POINT_SIZE_MAX, 2.5f);
            GLSMRasterizer.glPointParameterf(GL14.GL_POINT_FADE_THRESHOLD_SIZE, 4.5f);
            assertEquals(0.5f, GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MIN));
            assertEquals(2.5f, GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MAX));
            assertEquals(4.5f, GLSMQueries.glGetFloat(GL14.GL_POINT_FADE_THRESHOLD_SIZE));
            assertEquals(4.5f, GL11.glGetFloat(GL14.GL_POINT_FADE_THRESHOLD_SIZE));
            GLSMRasterizer.glPointParameterf(GL14.GL_POINT_SIZE_MIN, -1f);
            assertEquals(0.5f, GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MIN));

            attenuation.put(0, 0.5f).put(1, 0.25f).put(2, 0.125f);
            attenuation.position(0).limit(3);
            GLSMRasterizer.glPointParameter(GL14.GL_POINT_DISTANCE_ATTENUATION, attenuation);
            attenuation.clear();
            for (int i = 0; i < 16; i++) {
                attenuation.put(i, -777f);
            }
            GLSMQueries.glGetFloat(GL14.GL_POINT_DISTANCE_ATTENUATION, attenuation);
            assertEquals(0.5f, attenuation.get(0));
            assertEquals(0.25f, attenuation.get(1));
            assertEquals(0.125f, attenuation.get(2));

            GLSMRasterizer.glPointParameteri(GL20.GL_POINT_SPRITE_COORD_ORIGIN, GL20.GL_LOWER_LEFT);
            assertEquals(GL20.GL_LOWER_LEFT, GLSMQueries.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN));
            assertEquals(GL20.GL_LOWER_LEFT, GL11.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN));

            GLSMAttribStack.glPushAttrib(GL11.GL_POINT_BIT);
            GLSMRasterizer.glPointParameterf(GL14.GL_POINT_SIZE_MIN, 1f);
            GLSMRasterizer.glPointParameterf(GL14.GL_POINT_SIZE_MAX, 3f);
            GLSMRasterizer.glPointParameterf(GL14.GL_POINT_FADE_THRESHOLD_SIZE, 2f);
            attenuation.put(0, 1f).put(1, 1f).put(2, 1f);
            attenuation.position(0).limit(3);
            GLSMRasterizer.glPointParameter(GL14.GL_POINT_DISTANCE_ATTENUATION, attenuation);
            GLSMRasterizer.glPointParameteri(GL20.GL_POINT_SPRITE_COORD_ORIGIN, GL20.GL_UPPER_LEFT);
            assertEquals(1f, GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MIN));
            assertEquals(GL20.GL_UPPER_LEFT, GLSMQueries.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN));
            assertEquals(GL20.GL_UPPER_LEFT, GL11.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN));
            GLSMRasterizer.glPointParameterf(GL14.GL_POINT_SIZE_MIN, Float.NaN);
            assertTrue(Float.isNaN(GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MIN)));
            GLSMAttribStack.glPopAttrib();

            assertEquals(0.5f, GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MIN));
            assertEquals(2.5f, GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MAX));
            assertEquals(4.5f, GLSMQueries.glGetFloat(GL14.GL_POINT_FADE_THRESHOLD_SIZE));
            assertEquals(4.5f, GL11.glGetFloat(GL14.GL_POINT_FADE_THRESHOLD_SIZE));
            assertEquals(GL20.GL_LOWER_LEFT, GLSMQueries.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN));
            assertEquals(GL20.GL_LOWER_LEFT, GL11.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN));
            attenuation.clear();
            GLSMQueries.glGetFloat(GL14.GL_POINT_DISTANCE_ATTENUATION, attenuation);
            assertEquals(0.5f, attenuation.get(0));
            assertEquals(0.25f, attenuation.get(1));
            assertEquals(0.125f, attenuation.get(2));
        } finally {
            popAttribTo(base);
            assertEquals(savedMax, GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MAX));
        }
    }

    @Test
    void cacheOnlyEnablesRoundTrip() {
        final int base = GLSMAttribStack.getAttribDepth();
        final boolean savedSprite = GLSMEnable.glIsEnabled(GL20.GL_POINT_SPRITE);
        final boolean savedTwoSide = GLSMEnable.glIsEnabled(GL20.GL_VERTEX_PROGRAM_TWO_SIDE);
        final boolean savedEdge = GLSMQueries.glGetBoolean(GL11.GL_EDGE_FLAG);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        final boolean savedCube0 = GLSMEnable.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        final boolean savedCube1 = GLSMEnable.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        try {
            assertEnableRoundTrip(GL20.GL_POINT_SPRITE, false);
            assertEnableRoundTrip(GL20.GL_VERTEX_PROGRAM_TWO_SIDE, false);
            assertEnableRoundTrip(GL13.GL_TEXTURE_CUBE_MAP, false);

            GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLSMEnable.glEnable(GL20.GL_POINT_SPRITE);
            GLSMEnable.glEnable(GL20.GL_VERTEX_PROGRAM_TWO_SIDE);
            GLSMEnable.glEnable(GL13.GL_TEXTURE_CUBE_MAP);
            assertTrue(GLSMEnable.glIsEnabled(GL20.GL_POINT_SPRITE));
            assertTrue(GLSMEnable.glIsEnabled(GL20.GL_VERTEX_PROGRAM_TWO_SIDE));
            assertTrue(GLSMEnable.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP));
            GLSMAttribStack.glPopAttrib();
            assertFalse(GLSMEnable.glIsEnabled(GL20.GL_POINT_SPRITE));
            assertFalse(GLSMEnable.glIsEnabled(GL20.GL_VERTEX_PROGRAM_TWO_SIDE));
            assertFalse(GLSMEnable.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP));

            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            GLSMEnable.glDisable(GL13.GL_TEXTURE_CUBE_MAP);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMEnable.glEnable(GL13.GL_TEXTURE_CUBE_MAP);
            assertTrue(GLSMEnable.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP));
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            assertFalse(GLSMEnable.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP));
            assertFalse(GLSMQueries.glGetBoolean(GL13.GL_TEXTURE_CUBE_MAP));
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            assertTrue(GLSMQueries.glGetBoolean(GL13.GL_TEXTURE_CUBE_MAP));
            GLSMEnable.glDisable(GL13.GL_TEXTURE_CUBE_MAP);

            assertTrue(savedEdge);
            GLSMCurrentAttribs.glEdgeFlag(false);
            assertFalse(GLSMQueries.glGetBoolean(GL11.GL_EDGE_FLAG));
            GLSMAttribStack.glPushAttrib(GL11.GL_CURRENT_BIT);
            GLSMCurrentAttribs.glEdgeFlag(true);
            assertTrue(GLSMQueries.glGetBoolean(GL11.GL_EDGE_FLAG));
            GLSMAttribStack.glPopAttrib();
            assertFalse(GLSMQueries.glGetBoolean(GL11.GL_EDGE_FLAG));
        } finally {
            popAttribTo(base);
            GLSMCurrentAttribs.glEdgeFlag(savedEdge);
            setEnabled(GL20.GL_POINT_SPRITE, savedSprite);
            setEnabled(GL20.GL_VERTEX_PROGRAM_TWO_SIDE, savedTwoSide);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            setEnabled(GL13.GL_TEXTURE_CUBE_MAP, savedCube1);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            setEnabled(GL13.GL_TEXTURE_CUBE_MAP, savedCube0);
        }
    }
}
