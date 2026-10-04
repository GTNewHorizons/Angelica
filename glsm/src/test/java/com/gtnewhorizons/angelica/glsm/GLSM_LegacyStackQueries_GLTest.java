package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement.Usage;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.glsm.ffp.VertexKey;
import com.gtnewhorizons.angelica.glsm.states.PixelStoreState;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
public class GLSM_LegacyStackQueries_GLTest {

    private static final int[] SINGLE_VALUE_QUERIES = {
        GL11.GL_ATTRIB_STACK_DEPTH,
        GL11.GL_CLIENT_ATTRIB_STACK_DEPTH,
        GL11.GL_TEXTURE_STACK_DEPTH,
        GL11.GL_MAX_ATTRIB_STACK_DEPTH,
        GL11.GL_MAX_CLIENT_ATTRIB_STACK_DEPTH,
        GL11.GL_MAX_MODELVIEW_STACK_DEPTH,
        GL11.GL_MAX_PROJECTION_STACK_DEPTH,
        GL11.GL_MAX_TEXTURE_STACK_DEPTH,
        GL13.GL_MAX_TEXTURE_UNITS,
        GL11.GL_MAX_LIGHTS,
        GL11.GL_STENCIL_BITS,
        GL11.GL_DEPTH_BITS,
    };

    private static boolean positionArrayEnabled() {
        final VAOManager.Attrib position = GLStateManager.ctx().vaos.attrib(Usage.POSITION.getAttributeLocation());
        return position != null && position.enabled;
    }

    private static boolean colorArrayEnabled() {
        return (GLStateManager.ctx().vaos.getVertexFlags() & VertexFlags.COLOR_BIT) != 0;
    }

    private static void drainErrors() {
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
    }

    private static void drainClientAttribStack() {
        while (GLStateManager.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH) > 0) {
            GLStateManager.glPopClientAttrib();
        }
    }

    private static void drainAttribStack() {
        while (GLStateManager.getAttribDepth() > 0) {
            GLStateManager.glPopAttrib();
        }
    }

    private static int pushToLimit(int depthPname, int maxPname) {
        final int max = GLStateManager.glGetInteger(maxPname);
        for (int i = 0; i < max + 4; i++) {
            GLStateManager.glPushMatrix();
        }
        return GLStateManager.glGetInteger(depthPname);
    }

    private static void popToBase(int depthPname) {
        for (int i = 0; i < 128 && GLStateManager.glGetInteger(depthPname) > 1; i++) {
            GLStateManager.glPopMatrix();
        }
    }

    @Test
    void attribDepthTracksPushAndPop() {
        final int base = GLStateManager.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH);
        try {
            GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT);
            assertEquals(base + 1, GLStateManager.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH));
            GLStateManager.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
            assertEquals(base + 2, GLStateManager.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH));
            GLStateManager.glPopAttrib();
            assertEquals(base + 1, GLStateManager.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH));
            GLStateManager.glPopAttrib();
            assertEquals(base, GLStateManager.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH));
            assertEquals(GLStateManager.MAX_ATTRIB_STACK_DEPTH, GLStateManager.glGetInteger(GL11.GL_MAX_ATTRIB_STACK_DEPTH));
        } finally {
            while (GLStateManager.getAttribDepth() > base) {
                GLStateManager.glPopAttrib();
            }
        }
    }

    @Test
    void clientAttribDepthTracksPushAndPop() {
        try {
            assertEquals(0, GLStateManager.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH));
            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            assertEquals(1, GLStateManager.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH));
            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            assertEquals(2, GLStateManager.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH));
            GLStateManager.glPopClientAttrib();
            assertEquals(1, GLStateManager.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH));
            GLStateManager.glPopClientAttrib();
            assertEquals(0, GLStateManager.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH));
            GLStateManager.glPopClientAttrib();
            assertEquals(0, GLStateManager.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH));

            final int max = GLStateManager.glGetInteger(GL11.GL_MAX_CLIENT_ATTRIB_STACK_DEPTH);
            for (int i = 0; i < max + 2; i++) {
                GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            }
            assertEquals(max, GLStateManager.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH));
        } finally {
            drainClientAttribStack();
        }
    }

    @Test
    void textureStackDepthIsPerActiveUnit() {
        try {
            GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            assertEquals(1, GLStateManager.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH));
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glPushMatrix();
            assertEquals(2, GLStateManager.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH));
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            assertEquals(1, GLStateManager.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH));
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            assertEquals(2, GLStateManager.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH));
            GLStateManager.glPopMatrix();
            assertEquals(1, GLStateManager.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH));
        } finally {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
            popToBase(GL11.GL_TEXTURE_STACK_DEPTH);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    @Test
    void maxModelviewStackDepthIsExactlyReachable() {
        try {
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            assertEquals(1, GLStateManager.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH));
            assertEquals(GLStateManager.glGetInteger(GL11.GL_MAX_MODELVIEW_STACK_DEPTH), pushToLimit(GL11.GL_MODELVIEW_STACK_DEPTH, GL11.GL_MAX_MODELVIEW_STACK_DEPTH));
        } finally {
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            popToBase(GL11.GL_MODELVIEW_STACK_DEPTH);
        }
    }

    @Test
    void maxProjectionStackDepthIsExactlyReachable() {
        try {
            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            assertEquals(1, GLStateManager.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH));
            assertEquals(GLStateManager.glGetInteger(GL11.GL_MAX_PROJECTION_STACK_DEPTH), pushToLimit(GL11.GL_PROJECTION_STACK_DEPTH, GL11.GL_MAX_PROJECTION_STACK_DEPTH));
        } finally {
            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            popToBase(GL11.GL_PROJECTION_STACK_DEPTH);
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    @Test
    void maxTextureStackDepthIsExactlyReachable() {
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
            assertEquals(1, GLStateManager.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH));
            assertEquals(GLStateManager.glGetInteger(GL11.GL_MAX_TEXTURE_STACK_DEPTH), pushToLimit(GL11.GL_TEXTURE_STACK_DEPTH, GL11.GL_MAX_TEXTURE_STACK_DEPTH));
        } finally {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
            popToBase(GL11.GL_TEXTURE_STACK_DEPTH);
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    @Test
    void constantQueriesReportEmulatedLimits() {
        assertEquals(Math.min(VertexKey.MAX_UNITS, GLStateManager.MAX_TEXTURE_UNITS), GLStateManager.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS));
        assertEquals(8, GLStateManager.glGetInteger(GL11.GL_MAX_LIGHTS));
    }

    @Test
    void stencilAndDepthBitsMatchDefaultFramebuffer() {
        final GLContextState glCtx = GLStateManager.ctx();
        final int savedDraw = glCtx.drawFramebuffer;
        final int savedRead = glCtx.readFramebuffer;
        try {
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            drainErrors();
            assertEquals(
                GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL11.GL_STENCIL, GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE),
                GLStateManager.glGetInteger(GL11.GL_STENCIL_BITS));
            assertEquals(
                GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL11.GL_DEPTH, GL30.GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE),
                GLStateManager.glGetInteger(GL11.GL_DEPTH_BITS));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, savedDraw);
            GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, savedRead);
        }
    }

    @Test
    void stencilAndDepthBitsFollowBoundDrawFramebuffer() {
        final GLContextState glCtx = GLStateManager.ctx();
        final int savedDraw = glCtx.drawFramebuffer;
        final int savedRead = glCtx.readFramebuffer;
        final int fbo = GLStateManager.glGenFramebuffers();
        final int depthOnly = GLStateManager.glGenRenderbuffers();
        final int depthStencil = GLStateManager.glGenRenderbuffers();
        try {
            drainErrors();
            GLStateManager.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthOnly);
            GLStateManager.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, 16, 16);
            GLStateManager.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthStencil);
            GLStateManager.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, 16, 16);
            GLStateManager.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);

            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            assertEquals(0, GLStateManager.glGetInteger(GL11.GL_STENCIL_BITS));
            assertEquals(0, GLStateManager.glGetInteger(GL11.GL_DEPTH_BITS));

            GLStateManager.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthOnly);
            assertEquals(0, GLStateManager.glGetInteger(GL11.GL_STENCIL_BITS));
            final int depthOnlyBits = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE);
            assertTrue(depthOnlyBits >= 24, "driver depth size " + depthOnlyBits);
            assertEquals(depthOnlyBits, GLStateManager.glGetInteger(GL11.GL_DEPTH_BITS));

            GLStateManager.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthStencil);
            GLStateManager.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, depthStencil);
            assertEquals(8, GLStateManager.glGetInteger(GL11.GL_STENCIL_BITS));
            assertEquals(24, GLStateManager.glGetInteger(GL11.GL_DEPTH_BITS));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, savedDraw);
            GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, savedRead);
            GLStateManager.glDeleteFramebuffers(fbo);
            GLStateManager.glDeleteRenderbuffers(depthOnly);
            GLStateManager.glDeleteRenderbuffers(depthStencil);
        }
    }

    @Test
    void intBufferOverloadMatchesAndDriverIsNotAsked() {
        final int base = GLStateManager.getAttribDepth();
        final IntBuffer buf = BufferUtils.createIntBuffer(16);
        try {
            GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            drainErrors();
            GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH);
            assumeTrue(GL11.glGetError() != GL11.GL_NO_ERROR, "driver accepts GL_ATTRIB_STACK_DEPTH, so a clean error state proves nothing");
            for (final int pname : SINGLE_VALUE_QUERIES) {
                final int direct = GLStateManager.glGetInteger(pname);
                buf.put(0, -12345);
                GLStateManager.glGetInteger(pname, buf);
                assertEquals(direct, buf.get(0), "IntBuffer overload for 0x" + Integer.toHexString(pname));
                assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "driver error after querying 0x" + Integer.toHexString(pname));
            }
        } finally {
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            drainClientAttribStack();
            while (GLStateManager.getAttribDepth() > base) {
                GLStateManager.glPopAttrib();
            }
        }
    }

    @Test
    void attribDepthUnchangedWhileCompilingDisplayList() {
        final int base = GLStateManager.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH);
        final int list = GLStateManager.glGenLists(1);
        try {
            GLStateManager.glNewList(list, GL11.GL_COMPILE);
            GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT);
            assertEquals(base, GLStateManager.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH));
            GLStateManager.glPopAttrib();
            assertEquals(base, GLStateManager.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH));
            GLStateManager.glEndList();
            assertEquals(base, GLStateManager.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH));
        } finally {
            if (DisplayListManager.isRecording()) {
                GLStateManager.glEndList();
            }
            GLStateManager.glDeleteLists(list, 1);
        }
    }

    @Test
    void popAttribOnEmptyStackIsIgnored() {
        drainAttribStack();
        drainErrors();
        assertEquals(0, GLStateManager.getAttribDepth());
        assertDoesNotThrow(GLStateManager::glPopAttrib);
        assertEquals(0, GLStateManager.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH));
        assertDoesNotThrow(GLStateManager::glPopAttrib);
        assertEquals(0, GLStateManager.getAttribDepth());
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void pixelStoreBitRestoresPixelStoreAndPboBindings() {
        final GLContextState glCtx = GLStateManager.ctx();
        final int unpackBuffer = GLStateManager.glGenBuffers();
        final int packBuffer = GLStateManager.glGenBuffers();
        try {
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GLStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GLStateManager.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GLStateManager.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GLStateManager.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            final PixelStoreState unpackBefore = glCtx.pixelUnpackState;
            final PixelStoreState packBefore = glCtx.pixelPackState;

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GLStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 2);
            GLStateManager.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 7);
            GLStateManager.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            GLStateManager.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            assertEquals(1, glCtx.pixelUnpackState.alignment());
            assertEquals(2, glCtx.pixelPackState.alignment());
            assertEquals(7, glCtx.pixelPackState.rowLength());
            assertEquals(2, GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT));
            GLStateManager.glPopClientAttrib();

            assertSame(unpackBefore, glCtx.pixelUnpackState);
            assertSame(packBefore, glCtx.pixelPackState);
            assertEquals(4, GLStateManager.glGetInteger(GL11.GL_UNPACK_ALIGNMENT));
            assertEquals(0, GLStateManager.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING));
            assertEquals(0, GLStateManager.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING));
            assertEquals(4, GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT));
            assertEquals(4, GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT));
            assertEquals(0, GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH));
            assertEquals(0, GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING));
            assertEquals(0, GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            drainClientAttribStack();
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GLStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GLStateManager.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GLStateManager.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GLStateManager.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GLStateManager.glDeleteBuffers(unpackBuffer);
            GLStateManager.glDeleteBuffers(packBuffer);
        }
    }

    @Test
    void pixelStoreIsNotRestoredWithoutPixelStoreBit() {
        final GLContextState glCtx = GLStateManager.ctx();
        final int unpackBuffer = GLStateManager.glGenBuffers();
        final int packBuffer = GLStateManager.glGenBuffers();
        try {
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GLStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GLStateManager.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GLStateManager.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GLStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 2);
            GLStateManager.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            GLStateManager.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            GLStateManager.glPopClientAttrib();

            assertEquals(1, glCtx.pixelUnpackState.alignment());
            assertEquals(2, glCtx.pixelPackState.alignment());
            assertEquals(unpackBuffer, GLStateManager.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING));
            assertEquals(packBuffer, GLStateManager.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING));
            assertEquals(1, GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT));
            assertEquals(2, GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT));
            assertEquals(unpackBuffer, GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING));
            assertEquals(packBuffer, GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING));
        } finally {
            drainClientAttribStack();
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GLStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GLStateManager.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GLStateManager.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GLStateManager.glDeleteBuffers(unpackBuffer);
            GLStateManager.glDeleteBuffers(packBuffer);
        }
    }

    @Test
    void pixelStoreWithReturnsSameInstanceForUnchangedValue() {
        final GLContextState glCtx = GLStateManager.ctx();
        try {
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 9);
            GLStateManager.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 11);
            final PixelStoreState unpack = glCtx.pixelUnpackState;
            final PixelStoreState pack = glCtx.pixelPackState;

            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 9);
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpack.alignment());
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, 0);
            GLStateManager.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 11);
            GLStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, pack.alignment());
            GLStateManager.glPixelStorei(GL11.GL_PACK_LSB_FIRST, 0);

            assertSame(unpack, glCtx.pixelUnpackState);
            assertSame(pack, glCtx.pixelPackState);
            assertSame(unpack, unpack.with(GL11.GL_UNPACK_ROW_LENGTH, 9));
            assertSame(pack, pack.with(GL11.GL_PACK_ROW_LENGTH, 11));
        } finally {
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GLStateManager.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
        }
    }

    @Test
    void packAndUnpackRestoreToTheirOwnPriorValues() {
        final GLContextState glCtx = GLStateManager.ctx();
        try {
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 2);
            GLStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 8);
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 3);
            GLStateManager.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 5);

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GLStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 6);
            GLStateManager.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 7);
            assertEquals(1, glCtx.pixelUnpackState.alignment());
            assertEquals(4, glCtx.pixelPackState.alignment());
            assertEquals(6, glCtx.pixelUnpackState.skipRows());
            assertEquals(7, glCtx.pixelPackState.skipRows());
            assertEquals(6, GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS));
            assertEquals(7, GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS));
            GLStateManager.glPopClientAttrib();

            assertEquals(2, glCtx.pixelUnpackState.alignment());
            assertEquals(8, glCtx.pixelPackState.alignment());
            assertEquals(3, glCtx.pixelUnpackState.skipRows());
            assertEquals(5, glCtx.pixelPackState.skipRows());
            assertEquals(2, GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT));
            assertEquals(8, GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT));
            assertEquals(3, GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS));
            assertEquals(5, GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            drainClientAttribStack();
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GLStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GLStateManager.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
        }
    }

    @Test
    void vertexArrayEnableRestoredOnlyWithVertexArrayBit() {
        final int savedVao = GLStateManager.getBoundVAO();
        final int vao = GLStateManager.glGenVertexArrays();
        final IntBuffer enabled = BufferUtils.createIntBuffer(16);
        final int position = Usage.POSITION.getAttributeLocation();
        try {
            GLStateManager.glBindVertexArray(vao);
            drainErrors();
            assertFalse(positionArrayEnabled());

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            assertTrue(positionArrayEnabled());
            GLStateManager.glPopClientAttrib();
            assertFalse(positionArrayEnabled());
            GL20.glGetVertexAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED, enabled);
            assertEquals(GL11.GL_FALSE, enabled.get(0));

            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glPopClientAttrib();
            assertTrue(positionArrayEnabled());
            GL20.glGetVertexAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED, enabled);
            assertEquals(GL11.GL_TRUE, enabled.get(0));
            GLStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glPopClientAttrib();
            assertTrue(positionArrayEnabled());
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            drainClientAttribStack();
            GLStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glBindVertexArray(savedVao);
            GLStateManager.glDeleteVertexArrays(vao);
        }
    }

    @Test
    void vertexArrayStateRestoredOnlyWithVertexArrayBit() {
        final int savedVao = GLStateManager.getBoundVAO();
        final int vao = GLStateManager.glGenVertexArrays();
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            final int flagsBefore = GLStateManager.ctx().vaos.getVertexFlags();
            assertFalse(colorArrayEnabled());

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            assertTrue(colorArrayEnabled());
            GLStateManager.glPopClientAttrib();
            assertFalse(colorArrayEnabled());
            assertEquals(flagsBefore, GLStateManager.ctx().vaos.getVertexFlags());
            assertEquals(0, GLStateManager.getClientActiveTextureUnit());

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            GLStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glPopClientAttrib();
            assertTrue(colorArrayEnabled());
            assertEquals(1, GLStateManager.getClientActiveTextureUnit());
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            drainClientAttribStack();
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
            GLStateManager.glBindVertexArray(savedVao);
            GLStateManager.glDeleteVertexArrays(vao);
        }
    }

    @Test
    void intLightGettersConvertLikeMesa() {
        final FloatBuffer savedDiffuse = BufferUtils.createFloatBuffer(16);
        final FloatBuffer savedPosition = BufferUtils.createFloatBuffer(16);
        final FloatBuffer savedDirection = BufferUtils.createFloatBuffer(16);
        final FloatBuffer savedCutoff = BufferUtils.createFloatBuffer(16);
        final FloatBuffer savedAttenuation = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, savedDiffuse);
        GLStateManager.glGetLight(GL11.GL_LIGHT1, GL11.GL_POSITION, savedPosition);
        GLStateManager.glGetLight(GL11.GL_LIGHT1, GL11.GL_SPOT_DIRECTION, savedDirection);
        GLStateManager.glGetLight(GL11.GL_LIGHT1, GL11.GL_SPOT_CUTOFF, savedCutoff);
        GLStateManager.glGetLight(GL11.GL_LIGHT1, GL11.GL_LINEAR_ATTENUATION, savedAttenuation);
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        try {
            GLStateManager.glLoadIdentity();
            final FloatBuffer values = BufferUtils.createFloatBuffer(4);
            values.put(0, 0.25f).put(1, 0.5f).put(2, 1.0f).put(3, -0.5f);
            GLStateManager.glLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, values);
            values.put(0, 3.9f).put(1, -2.7f).put(2, 0.5f).put(3, 1.0f);
            GLStateManager.glLight(GL11.GL_LIGHT1, GL11.GL_POSITION, values);
            values.put(0, 2.5f).put(1, -1.5f).put(2, -3.9f).put(3, 0.0f);
            GLStateManager.glLight(GL11.GL_LIGHT1, GL11.GL_SPOT_DIRECTION, values);
            GLStateManager.glLightf(GL11.GL_LIGHT1, GL11.GL_SPOT_CUTOFF, 45.75f);
            GLStateManager.glLightf(GL11.GL_LIGHT1, GL11.GL_LINEAR_ATTENUATION, 2.5f);
            drainErrors();

            ints.position(2);
            GLStateManager.glGetLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, ints);
            assertEquals(2, ints.position());
            assertEquals(536870911, ints.get(2));
            assertEquals(1073741823, ints.get(3));
            assertEquals(2147483647, ints.get(4));
            assertEquals(-1073741823, ints.get(5));
            ints.position(0);

            GLStateManager.glGetLight(GL11.GL_LIGHT1, GL11.GL_POSITION, ints);
            assertEquals(3, ints.get(0));
            assertEquals(-2, ints.get(1));
            assertEquals(0, ints.get(2));
            assertEquals(1, ints.get(3));

            ints.put(3, -777);
            GLStateManager.glGetLight(GL11.GL_LIGHT1, GL11.GL_SPOT_DIRECTION, ints);
            assertEquals(2, ints.get(0));
            assertEquals(-1, ints.get(1));
            assertEquals(-3, ints.get(2));
            assertEquals(-777, ints.get(3));

            ints.put(1, -777);
            GLStateManager.glGetLight(GL11.GL_LIGHT1, GL11.GL_SPOT_CUTOFF, ints);
            assertEquals(45, ints.get(0));
            assertEquals(-777, ints.get(1));
            GLStateManager.glGetLight(GL11.GL_LIGHT1, GL11.GL_LINEAR_ATTENUATION, ints);
            assertEquals(2, ints.get(0));

            ints.put(0, -777);
            GLStateManager.glGetLight(GL11.GL_LIGHT1, GL11.GL_EMISSION, ints);
            assertEquals(-777, ints.get(0));
            GLStateManager.glGetLight(GL11.GL_LIGHT1, GL11.GL_SHININESS, ints);
            assertEquals(-777, ints.get(0));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glLoadIdentity();
            savedDiffuse.limit(4);
            GLStateManager.glLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, savedDiffuse);
            savedPosition.limit(4);
            GLStateManager.glLight(GL11.GL_LIGHT1, GL11.GL_POSITION, savedPosition);
            savedDirection.limit(4);
            GLStateManager.glLight(GL11.GL_LIGHT1, GL11.GL_SPOT_DIRECTION, savedDirection);
            GLStateManager.glLightf(GL11.GL_LIGHT1, GL11.GL_SPOT_CUTOFF, savedCutoff.get(0));
            GLStateManager.glLightf(GL11.GL_LIGHT1, GL11.GL_LINEAR_ATTENUATION, savedAttenuation.get(0));
            GLStateManager.glPopMatrix();
        }
    }

    @Test
    void intMaterialGettersConvertLikeMesa() {
        final FloatBuffer savedSpecular = BufferUtils.createFloatBuffer(16);
        final FloatBuffer savedShininess = BufferUtils.createFloatBuffer(16);
        final FloatBuffer savedIndexes = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetMaterial(GL11.GL_FRONT, GL11.GL_SPECULAR, savedSpecular);
        GLStateManager.glGetMaterial(GL11.GL_FRONT, GL11.GL_SHININESS, savedShininess);
        GLStateManager.glGetMaterial(GL11.GL_BACK, GL11.GL_COLOR_INDEXES, savedIndexes);
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        try {
            final FloatBuffer values = BufferUtils.createFloatBuffer(4);
            values.put(0, 0.5f).put(1, 0.75f).put(2, 1.0f).put(3, 0.0f);
            GLStateManager.glMaterial(GL11.GL_FRONT, GL11.GL_SPECULAR, values);
            GLStateManager.glMaterialf(GL11.GL_FRONT, GL11.GL_SHININESS, 12.6f);
            values.put(0, 2.5f).put(1, -2.5f).put(2, 7.4f).put(3, 0.0f);
            GLStateManager.glMaterial(GL11.GL_BACK, GL11.GL_COLOR_INDEXES, values);
            drainErrors();

            ints.position(1);
            GLStateManager.glGetMaterial(GL11.GL_FRONT, GL11.GL_SPECULAR, ints);
            assertEquals(1, ints.position());
            assertEquals(1073741823, ints.get(1));
            assertEquals(1610612735, ints.get(2));
            assertEquals(2147483647, ints.get(3));
            assertEquals(0, ints.get(4));
            ints.position(0);

            ints.put(1, -777);
            GLStateManager.glGetMaterial(GL11.GL_FRONT, GL11.GL_SHININESS, ints);
            assertEquals(13, ints.get(0));
            assertEquals(-777, ints.get(1));

            ints.put(3, -777);
            GLStateManager.glGetMaterial(GL11.GL_BACK, GL11.GL_COLOR_INDEXES, ints);
            assertEquals(3, ints.get(0));
            assertEquals(-3, ints.get(1));
            assertEquals(7, ints.get(2));
            assertEquals(-777, ints.get(3));

            ints.put(0, -777);
            GLStateManager.glGetMaterial(GL11.GL_FRONT, GL11.GL_POSITION, ints);
            assertEquals(-777, ints.get(0));
            GLStateManager.glGetMaterial(GL11.GL_FRONT, GL11.GL_SPOT_CUTOFF, ints);
            assertEquals(-777, ints.get(0));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            savedSpecular.limit(4);
            GLStateManager.glMaterial(GL11.GL_FRONT, GL11.GL_SPECULAR, savedSpecular);
            GLStateManager.glMaterialf(GL11.GL_FRONT, GL11.GL_SHININESS, savedShininess.get(0));
            savedIndexes.limit(4);
            GLStateManager.glMaterial(GL11.GL_BACK, GL11.GL_COLOR_INDEXES, savedIndexes);
        }
    }

    @Test
    void currentTextureCoordsFollowActiveUnit() {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(16);
        final float[][] saved = new float[4][4];
        try {
            for (int unit = 0; unit < 4; unit++) {
                GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                GLStateManager.glGetFloat(GL11.GL_CURRENT_TEXTURE_COORDS, buf);
                for (int i = 0; i < 4; i++) {
                    saved[unit][i] = buf.get(i);
                }
            }
            GLStateManager.glMultiTexCoord2f(GL13.GL_TEXTURE0, 0.25f, 0.5f);
            GLStateManager.glMultiTexCoord2f(GL13.GL_TEXTURE1, 240.0f, 16.0f);
            GLStateManager.glMultiTexCoord2f(GL13.GL_TEXTURE2, 0.75f, 0.125f);
            GLStateManager.glMultiTexCoord2f(GL13.GL_TEXTURE3, 0.375f, 0.625f);
            drainErrors();

            final float[][] expected = { { 0.25f, 0.5f }, { 240.0f, 16.0f }, { 0.75f, 0.125f }, { 0.375f, 0.625f } };
            for (int unit = 0; unit < 4; unit++) {
                GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                GLStateManager.glGetFloat(GL11.GL_CURRENT_TEXTURE_COORDS, buf);
                assertEquals(expected[unit][0], buf.get(0), "s of unit " + unit);
                assertEquals(expected[unit][1], buf.get(1), "t of unit " + unit);
                assertEquals(0.0f, buf.get(2), "r of unit " + unit);
                assertEquals(1.0f, buf.get(3), "q of unit " + unit);
            }
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            for (int unit = 0; unit < 4; unit++) {
                GLStateManager.glMultiTexCoord2f(GL13.GL_TEXTURE0 + unit, saved[unit][0], saved[unit][1]);
            }
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        }
    }

    @Test
    void currentTextureCoordsOfUntrackedUnitAreInitialValue() {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(16);
        final int unit = GLStateManager.ctx().ffp.currentTexCoords.length + 1;
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glMultiTexCoord2f(GL13.GL_TEXTURE0, 0.25f, 0.5f);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            drainErrors();
            for (int i = 0; i < 4; i++) {
                buf.put(i, -7.0f);
            }
            GLStateManager.glGetFloat(GL11.GL_CURRENT_TEXTURE_COORDS, buf);
            assertEquals(0.0f, buf.get(0));
            assertEquals(0.0f, buf.get(1));
            assertEquals(0.0f, buf.get(2));
            assertEquals(1.0f, buf.get(3));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glMultiTexCoord2f(GL13.GL_TEXTURE0, 0.0f, 0.0f);
        }
    }
}
