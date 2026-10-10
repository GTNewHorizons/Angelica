package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement.Usage;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.glsm.ffp.VertexKey;
import com.gtnewhorizons.angelica.glsm.states.PixelStoreState;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
public class GLSM_LegacyStackQueries_GLTest {

    private int savedVao;
    private int vao;
    private int vbo;

    @BeforeEach
    void bindFreshVertexArray() {
        savedVao = GLStateManager.getBoundVAO();
        vao = GLStateManager.glGenVertexArrays();
        vbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindVertexArray(vao);
    }

    @AfterEach
    void deleteVertexArray() {
        GLStateManager.glBindVertexArray(savedVao);
        GLStateManager.glDeleteVertexArrays(vao);
        GLStateManager.glDeleteBuffers(vbo);
    }

    private static final int[] LEGACY_PNAMES = legacyPnames();

    private static int[] legacyPnames() {
        final IntArrayList out = new IntArrayList();
        for (int pname = 0; pname <= 0xFFFF; pname++) {
            if (LegacyQueries.desc(pname) != 0) {
                out.add(pname);
            }
        }
        return out.toIntArray();
    }

    private static void setEnabled(int cap, boolean on) {
        if (on) {
            GLStateManager.glEnable(cap);
        } else {
            GLStateManager.glDisable(cap);
        }
    }

    private static boolean attribHasSize3(Usage usage) {
        final VAOManager.Attrib attrib = GLStateManager.ctx().vaos.attrib(usage.getAttributeLocation());
        return attrib != null && attrib.size == 3;
    }

    private static String hex(int pname) {
        return "0x" + Integer.toHexString(pname);
    }

    private static void assumeDriverRejectsLegacyPnames() {
        drainErrors();
        GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH);
        assumeTrue(GL11.glGetError() != GL11.GL_NO_ERROR, "driver accepts GL_ATTRIB_STACK_DEPTH, so a clean error state proves nothing");
    }

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
        }
    }

    @Test
    void vertexArrayStateRestoredOnlyWithVertexArrayBit() {
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

    @Test
    void legacyPnamesAreRejectedByTheDriver() {
        assumeDriverRejectsLegacyPnames();
        final List<String> accepted = new ArrayList<>();
        for (final int pname : LEGACY_PNAMES) {
            drainErrors();
            GL11.glGetInteger(pname);
            if (GL11.glGetError() == GL11.GL_NO_ERROR) {
                accepted.add(hex(pname));
            }
        }
        assertTrue(accepted.isEmpty(), "driver accepts pnames that the cache answers: " + accepted);
    }

    @Test
    void legacyQueriesNeverReachTheDriver() {
        final int base = GLStateManager.getAttribDepth();
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        final FloatBuffer floats = BufferUtils.createFloatBuffer(16);
        final DoubleBuffer doubles = BufferUtils.createDoubleBuffer(16);
        final ByteBuffer bytes = BufferUtils.createByteBuffer(16);
        final int[] intArray = new int[16];
        final float[] floatArray = new float[16];
        final double[] doubleArray = new double[16];
        try {
            GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            assumeDriverRejectsLegacyPnames();
            for (final int pname : LEGACY_PNAMES) {
                drainErrors();
                final int scalar = GLStateManager.glGetInteger(pname);
                GLStateManager.glGetFloat(pname);
                GLStateManager.glGetBoolean(pname);
                GLStateManager.glIsEnabled(pname);
                ints.clear();
                ints.put(0, -12345);
                GLStateManager.glGetInteger(pname, ints);
                floats.clear();
                GLStateManager.glGetFloat(pname, floats);
                doubles.clear();
                GLStateManager.glGetDouble(pname, doubles);
                bytes.clear();
                GLStateManager.glGetBoolean(pname, bytes);
                intArray[0] = -12345;
                GLStateManager.glGetInteger(pname, intArray);
                GLStateManager.glGetFloat(pname, floatArray);
                GLStateManager.glGetDouble(pname, doubleArray);
                if (LegacyQueries.count(LegacyQueries.desc(pname)) == 1) {
                    assertEquals(scalar, ints.get(0), "IntBuffer overload for " + hex(pname));
                    assertEquals(scalar, intArray[0], "int[] overload for " + hex(pname));
                }
                assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "driver error after querying " + hex(pname));
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
    void typedEntryPointsAgreeUnderMesaConversions() {
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        final FloatBuffer floats = BufferUtils.createFloatBuffer(16);
        final DoubleBuffer doubles = BufferUtils.createDoubleBuffer(16);
        final FloatBuffer savedColor = BufferUtils.createFloatBuffer(16);
        final FloatBuffer savedNormal = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_CURRENT_COLOR, savedColor);
        GLStateManager.glGetFloat(GL11.GL_CURRENT_NORMAL, savedNormal);
        final int savedFunc = GLStateManager.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        final float savedRef = GLStateManager.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        final float savedStart = GLStateManager.glGetFloat(GL11.GL_FOG_START);
        final float savedEnd = GLStateManager.glGetFloat(GL11.GL_FOG_END);
        final float savedDensity = GLStateManager.glGetFloat(GL11.GL_FOG_DENSITY);
        final boolean savedLighting = GLStateManager.glIsEnabled(GL11.GL_LIGHTING);
        final boolean savedFog = GLStateManager.glIsEnabled(GL11.GL_FOG);
        final int savedShade = GLStateManager.glGetInteger(GL11.GL_SHADE_MODEL);
        final int savedHint = GLStateManager.glGetInteger(GL11.GL_FOG_HINT);
        final float savedMinSize = GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MIN);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        try {
            GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5f);
            GLStateManager.glFogf(GL11.GL_FOG_START, -1.5f);
            GLStateManager.glFogf(GL11.GL_FOG_END, 2.5f);
            GLStateManager.glFogf(GL11.GL_FOG_DENSITY, 0.25f);
            GLStateManager.glColor4f(0.25f, 0.5f, 0.75f, 1f);
            GLStateManager.glNormal3f(0f, -0.5f, 1f);
            GLStateManager.glEnable(GL11.GL_LIGHTING);
            GLStateManager.glEnable(GL11.GL_FOG);
            GLStateManager.glShadeModel(GL11.GL_FLAT);
            GLStateManager.glHint(GL11.GL_FOG_HINT, GL11.GL_NICEST);
            GLStateManager.glPointParameterf(GL14.GL_POINT_SIZE_MIN, 2.5f);
            GLStateManager.glTranslatef(1.5f, -2.5f, 3f);
            for (final int pname : LEGACY_PNAMES) {
                final int desc = LegacyQueries.desc(pname);
                final int type = LegacyQueries.type(desc);
                final int count = LegacyQueries.count(desc);
                final String name = hex(pname);
                if (count == 1) {
                    if (type == LegacyQueries.BOOL) {
                        final boolean b = GLStateManager.glGetBoolean(pname);
                        assertEquals(b ? 1 : 0, GLStateManager.glGetInteger(pname), "int of bool " + name);
                        assertEquals(b ? 1.0f : 0.0f, GLStateManager.glGetFloat(pname), "float of bool " + name);
                    } else if (type == LegacyQueries.INT) {
                        final int i = GLStateManager.glGetInteger(pname);
                        assertEquals((float) i, GLStateManager.glGetFloat(pname), "float of int " + name);
                        assertEquals(i != 0, GLStateManager.glGetBoolean(pname), "bool of int " + name);
                    } else {
                        final float f = GLStateManager.glGetFloat(pname);
                        final int converted = type == LegacyQueries.FLOATN ? LegacyQueries.floatToInt(f) : LegacyQueries.lround(f);
                        assertEquals(converted, GLStateManager.glGetInteger(pname), "int of float " + name);
                        assertEquals(f != 0.0f, GLStateManager.glGetBoolean(pname), "bool of float " + name);
                    }
                } else {
                    floats.clear();
                    GLStateManager.glGetFloat(pname, floats);
                    ints.clear();
                    GLStateManager.glGetInteger(pname, ints);
                    doubles.clear();
                    GLStateManager.glGetDouble(pname, doubles);
                    for (int i = 0; i < count; i++) {
                        final float f = floats.get(i);
                        final int converted = type == LegacyQueries.FLOATN ? LegacyQueries.floatToInt(f) : LegacyQueries.lround(f);
                        assertEquals(converted, ints.get(i), "int element " + i + " of " + name);
                        assertEquals((double) f, doubles.get(i), "double element " + i + " of " + name);
                    }
                    assertEquals(floats.get(0), GLStateManager.glGetFloat(pname), "scalar float of " + name);
                }
            }
        } finally {
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glPopMatrix();
            GLStateManager.glPointParameterf(GL14.GL_POINT_SIZE_MIN, savedMinSize);
            GLStateManager.glHint(GL11.GL_FOG_HINT, savedHint);
            GLStateManager.glShadeModel(savedShade);
            setEnabled(GL11.GL_FOG, savedFog);
            setEnabled(GL11.GL_LIGHTING, savedLighting);
            GLStateManager.glNormal3f(savedNormal.get(0), savedNormal.get(1), savedNormal.get(2));
            GLStateManager.glColor4f(savedColor.get(0), savedColor.get(1), savedColor.get(2), savedColor.get(3));
            GLStateManager.glFogf(GL11.GL_FOG_DENSITY, savedDensity);
            GLStateManager.glFogf(GL11.GL_FOG_END, savedEnd);
            GLStateManager.glFogf(GL11.GL_FOG_START, savedStart);
            GLStateManager.glAlphaFunc(savedFunc, savedRef);
        }
    }

    @Test
    void clientActiveTextureRoundTrips() {
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            drainErrors();
            assertEquals(GLStateManager.MAX_TEXTURE_COORDS, GLStateManager.glGetInteger(GL20.GL_MAX_TEXTURE_COORDS));

            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            assertEquals(GL13.GL_TEXTURE1, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            GLStateManager.glClientActiveTexture(0);
            assertEquals(GL13.GL_TEXTURE1, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0 + GLStateManager.MAX_TEXTURE_UNITS);
            assertEquals(GL13.GL_TEXTURE1, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0 + GLStateManager.MAX_TEXTURE_COORDS);
            assertEquals(GL13.GL_TEXTURE1, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE), "units past GL_MAX_TEXTURE_COORDS are rejected");
            assertEquals(1, GLStateManager.getClientActiveTextureUnit());

            final int saved = GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            assertEquals(GL13.GL_TEXTURE0, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            GLStateManager.glClientActiveTexture(saved);
            assertEquals(GL13.GL_TEXTURE1, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));

            GLStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, 0, 0L);
            final VAOManager.Attrib attrib = GLStateManager.ctx().vaos.attrib(Usage.SECONDARY_UV.getAttributeLocation());
            assertNotNull(attrib);
            assertEquals(2, attrib.size);
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());

            assumeTrue(GLStateManager.MAX_TEXTURE_COORDS > 5);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE5);
            assertEquals(GL13.GL_TEXTURE5, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            GLStateManager.glTexCoordPointer(3, GL11.GL_FLOAT, 0, 0L);
            assertEquals(3, GLStateManager.glGetInteger(GL11.GL_TEXTURE_COORD_ARRAY_SIZE));
            assertFalse(attribHasSize3(Usage.PRIMARY_UV));
            assertFalse(attribHasSize3(Usage.SECONDARY_UV));
            GLStateManager.glClientActiveTexture(0);
            assertEquals(GL13.GL_TEXTURE5, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0 + GLStateManager.MAX_TEXTURE_UNITS);
            assertEquals(GL13.GL_TEXTURE5, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    private static float driverMaxPointSize() {
        final FloatBuffer range = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_POINT_SIZE_RANGE, range);
        final float smooth = range.get(1);
        GL11.glGetFloat(GL12.GL_ALIASED_POINT_SIZE_RANGE, range);
        return Math.max(smooth, range.get(1));
    }

    private static int driverAttrib(int index, int pname) {
        final IntBuffer out = BufferUtils.createIntBuffer(16);
        GL20.glGetVertexAttrib(index, pname, out);
        return out.get(0);
    }

    @Test
    void conventionalPointerSurvivesGenericOverride() {
        final int position = Usage.POSITION.getAttributeLocation();
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            drainErrors();

            GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glVertexAttribPointer(position, 2, GL11.GL_FLOAT, false, 8, 4L);
            assertEquals(3, VAOManager.get(position).size, "disabled generic pointer must not displace an enabled conventional array");
            assertEquals(3, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));

            GLStateManager.glEnableVertexAttribArray(position);
            assertEquals(2, VAOManager.get(position).size, "enabled generic attribute wins");
            assertTrue(VAOManager.get(position).genericPointer);
            assertEquals(2, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));
            assertEquals(8, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE));

            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glDisableVertexAttribArray(position);
            assertEquals(3, VAOManager.get(position).size, "conventional pointer must come back when the generic attribute is disabled");
            assertEquals(12, VAOManager.get(position).stride);
            assertFalse(VAOManager.get(position).genericPointer);
            assertEquals(3, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));
            assertEquals(12, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE));
            assertEquals(vbo, driverAttrib(position, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING));
            assertEquals(GL11.GL_TRUE, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED), "conventional array stays enabled at draw level");
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_VERTEX_ARRAY));
            assertEquals(0, GLStateManager.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING), "repoint must restore the array buffer binding");
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glDisableVertexAttribArray(position);
            GLStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void conventionalPointerSetUnderEnabledGenericAppliesOnceGenericIsDisabled() {
        final int position = Usage.POSITION.getAttributeLocation();
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            drainErrors();

            GLStateManager.glVertexAttribPointer(position, 2, GL11.GL_FLOAT, false, 8, 0L);
            GLStateManager.glEnableVertexAttribArray(position);
            GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            assertEquals(2, VAOManager.get(position).size);
            assertEquals(2, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));

            GLStateManager.glDisableVertexAttribArray(position);
            assertEquals(3, VAOManager.get(position).size);
            assertEquals(3, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));
            assertEquals(GL11.GL_TRUE, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));

            GLStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            assertEquals(GL11.GL_FALSE, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            assertFalse(positionArrayEnabled());
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glDisableVertexAttribArray(position);
            GLStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void drawVertexFlagsDoNotAnswerClientArrayEnables() {
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            VAOManager.setCurrentVertexFlags(VertexFlags.COLOR_BIT | VertexFlags.NORMAL_BIT | VertexFlags.TEXTURE_BIT);
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_COLOR_ARRAY));
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_NORMAL_ARRAY));
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY));

            GLStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
            VAOManager.setCurrentVertexFlags(0);
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_COLOR_ARRAY));
        } finally {
            GLStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
            VAOManager.setCurrentVertexFlags(0);
        }
    }

    private static void assertClientArray(String name, int sizePname, int size, int typePname, int type, int stridePname, int stride, int bindingPname, int binding) {
        if (sizePname != 0) assertEquals(size, GLStateManager.glGetInteger(sizePname), name + " size");
        if (typePname != 0) assertEquals(type, GLStateManager.glGetInteger(typePname), name + " type");
        assertEquals(stride, GLStateManager.glGetInteger(stridePname), name + " stride");
        assertEquals(binding, GLStateManager.glGetInteger(bindingPname), name + " buffer binding");
    }

    private static void assertVertexArray(String name, int size, int type, int stride, int binding) {
        assertClientArray(name, GL11.GL_VERTEX_ARRAY_SIZE, size, GL11.GL_VERTEX_ARRAY_TYPE, type, GL11.GL_VERTEX_ARRAY_STRIDE, stride, GL15.GL_VERTEX_ARRAY_BUFFER_BINDING, binding);
    }

    private static void assertTexCoordArray(String name, int size, int type, int stride, int binding) {
        assertClientArray(name, GL11.GL_TEXTURE_COORD_ARRAY_SIZE, size, GL11.GL_TEXTURE_COORD_ARRAY_TYPE, type, GL11.GL_TEXTURE_COORD_ARRAY_STRIDE, stride,
            GL15.GL_TEXTURE_COORD_ARRAY_BUFFER_BINDING, binding);
    }

    @Test
    void clientArrayPointerQueriesAreAnsweredFromCache() {
        final int vaoB = GLStateManager.glGenVertexArrays();
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            drainErrors();

            assertVertexArray("default vertex", 4, GL11.GL_FLOAT, 0, 0);
            assertClientArray("default normal", 0, 0, GL11.GL_NORMAL_ARRAY_TYPE, GL11.GL_FLOAT, GL11.GL_NORMAL_ARRAY_STRIDE, 0, GL15.GL_NORMAL_ARRAY_BUFFER_BINDING, 0);
            assertClientArray("default color", GL11.GL_COLOR_ARRAY_SIZE, 4, GL11.GL_COLOR_ARRAY_TYPE, GL11.GL_FLOAT, GL11.GL_COLOR_ARRAY_STRIDE, 0, GL15.GL_COLOR_ARRAY_BUFFER_BINDING, 0);
            assertClientArray("default secondary color", GL14.GL_SECONDARY_COLOR_ARRAY_SIZE, 3, GL14.GL_SECONDARY_COLOR_ARRAY_TYPE, GL11.GL_FLOAT,
                GL14.GL_SECONDARY_COLOR_ARRAY_STRIDE, 0, GL15.GL_SECONDARY_COLOR_ARRAY_BUFFER_BINDING, 0);
            assertClientArray("default fog coord", 0, 0, GL14.GL_FOG_COORDINATE_ARRAY_TYPE, GL11.GL_FLOAT, GL14.GL_FOG_COORDINATE_ARRAY_STRIDE, 0,
                GL15.GL_FOG_COORDINATE_ARRAY_BUFFER_BINDING, 0);
            assertClientArray("default index", 0, 0, GL11.GL_INDEX_ARRAY_TYPE, GL11.GL_FLOAT, GL11.GL_INDEX_ARRAY_STRIDE, 0, GL15.GL_INDEX_ARRAY_BUFFER_BINDING, 0);
            assertClientArray("default edge flag", 0, 0, 0, 0, GL11.GL_EDGE_FLAG_ARRAY_STRIDE, 0, GL15.GL_EDGE_FLAG_ARRAY_BUFFER_BINDING, 0);
            assertTexCoordArray("default texcoord", 4, GL11.GL_FLOAT, 0, 0);
            assertNull(GLStateManager.glGetPointer(GL11.GL_VERTEX_ARRAY_POINTER, 4));

            GLStateManager.glVertexPointer(3, GL11.GL_SHORT, 20, 8L);
            GLStateManager.glNormalPointer(GL11.GL_BYTE, 16, 4L);
            GLStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 16, 12L);
            GLStateManager.glSecondaryColorPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLStateManager.glFogCoordPointer(GL11.GL_FLOAT, 4, 0L);
            GLStateManager.glEdgeFlagPointer(7, 0L);
            GLStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, 8, 0L);

            assertVertexArray("vertex", 3, GL11.GL_SHORT, 20, vbo);
            assertClientArray("normal", 0, 0, GL11.GL_NORMAL_ARRAY_TYPE, GL11.GL_BYTE, GL11.GL_NORMAL_ARRAY_STRIDE, 16, GL15.GL_NORMAL_ARRAY_BUFFER_BINDING, vbo);
            assertClientArray("color", GL11.GL_COLOR_ARRAY_SIZE, 4, GL11.GL_COLOR_ARRAY_TYPE, GL11.GL_UNSIGNED_BYTE, GL11.GL_COLOR_ARRAY_STRIDE, 16,
                GL15.GL_COLOR_ARRAY_BUFFER_BINDING, vbo);
            assertClientArray("secondary color", GL14.GL_SECONDARY_COLOR_ARRAY_SIZE, 3, GL14.GL_SECONDARY_COLOR_ARRAY_TYPE, GL11.GL_FLOAT,
                GL14.GL_SECONDARY_COLOR_ARRAY_STRIDE, 12, GL15.GL_SECONDARY_COLOR_ARRAY_BUFFER_BINDING, vbo);
            assertClientArray("fog coord", 0, 0, GL14.GL_FOG_COORDINATE_ARRAY_TYPE, GL11.GL_FLOAT, GL14.GL_FOG_COORDINATE_ARRAY_STRIDE, 4,
                GL15.GL_FOG_COORDINATE_ARRAY_BUFFER_BINDING, vbo);
            assertClientArray("edge flag", 0, 0, 0, 0, GL11.GL_EDGE_FLAG_ARRAY_STRIDE, 7, GL15.GL_EDGE_FLAG_ARRAY_BUFFER_BINDING, vbo);
            assertTexCoordArray("texcoord unit 0", 2, GL11.GL_FLOAT, 8, vbo);

            assertEquals(3.0f, GLStateManager.glGetFloat(GL11.GL_VERTEX_ARRAY_SIZE));
            GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_STRIDE, ints);
            assertEquals(20, ints.get(0));
            assertEquals(8L, MemoryUtilities.memAddress(GLStateManager.glGetPointer(GL11.GL_VERTEX_ARRAY_POINTER, 4)));
            assertEquals(12L, MemoryUtilities.memAddress(GLStateManager.glGetPointer(GL11.GL_COLOR_ARRAY_POINTER, 4)));

            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0 + GLStateManager.MAX_TEXTURE_COORDS - 1);
            assertTexCoordArray("unset texcoord unit", 4, GL11.GL_FLOAT, 0, 0);
            GLStateManager.glTexCoordPointer(3, GL11.GL_SHORT, 6, 0L);
            assertTexCoordArray("last texcoord unit", 3, GL11.GL_SHORT, 6, vbo);
            assertFalse(attribHasSize3(Usage.PRIMARY_UV));
            assertFalse(attribHasSize3(Usage.SECONDARY_UV));
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            assertTexCoordArray("texcoord unit 0 again", 2, GL11.GL_FLOAT, 8, vbo);

            GLStateManager.glBindVertexArray(vaoB);
            assertVertexArray("other vao", 4, GL11.GL_FLOAT, 0, 0);
            final FloatBuffer client = BufferUtils.createFloatBuffer(12);
            GLStateManager.glVertexPointer(2, 0, client);
            assertVertexArray("client pointer", 2, GL11.GL_FLOAT, 0, 0);
            assertEquals(MemoryUtilities.memAddress(client), MemoryUtilities.memAddress(GLStateManager.glGetPointer(GL11.GL_VERTEX_ARRAY_POINTER, 4)));
            GLStateManager.glBindVertexArray(vao);
            assertVertexArray("first vao again", 3, GL11.GL_SHORT, 20, vbo);
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glDeleteVertexArrays(vaoB);
        }
    }

    @Test
    void interleavedArraysSetClientStateWithoutAnActiveFfpProgram() {
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            drainErrors();
            assumeFalse(GLStateManager.ctx().ffp.isActive());

            GLStateManager.glInterleavedArrays(GL11.GL_C4UB_V3F, 0, 0L);
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_VERTEX_ARRAY));
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_COLOR_ARRAY));
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_NORMAL_ARRAY));
            assertVertexArray("interleaved vertex", 3, GL11.GL_FLOAT, 16, vbo);
            assertEquals(GL11.GL_UNSIGNED_BYTE, GLStateManager.glGetInteger(GL11.GL_COLOR_ARRAY_TYPE));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            for (final int cap : CLIENT_ARRAY_CAPS) {
                GLStateManager.glDisableClientState(cap);
            }
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void clientVertexArrayBitRestoresPointersEnablesAndBindings() {
        final int vaoB = GLStateManager.glGenVertexArrays();
        final int vbo2 = GLStateManager.glGenBuffers();
        final int ebo = GLStateManager.glGenBuffers();
        final int position = Usage.POSITION.getAttributeLocation();
        final int color = Usage.COLOR.getAttributeLocation();
        final int normal = Usage.NORMAL.getAttributeLocation();
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            drainErrors();
            GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 16, 12L);
            GLStateManager.glFogCoordPointer(GL11.GL_FLOAT, 4, 0L);
            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo2);
            GLStateManager.glVertexPointer(2, GL11.GL_SHORT, 4, 4L);
            GLStateManager.glFogCoordPointer(GL11.GL_FLOAT, 8, 0L);
            GLStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
            GLStateManager.glEnableClientState(GL11.GL_NORMAL_ARRAY);
            GLStateManager.glVertexAttribPointer(5, 1, GL11.GL_FLOAT, false, 4, 0L);
            GLStateManager.glEnableVertexAttribArray(5);
            GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glBindVertexArray(vaoB);
            GLStateManager.glPopClientAttrib();

            assertEquals(vao, GLStateManager.getBoundVAO());
            assertEquals(vbo, GLStateManager.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING));
            assertEquals(ebo, GLStateManager.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING));
            assertEquals(ebo, GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING));
            assertEquals(GL13.GL_TEXTURE0, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            assertVertexArray("restored vertex", 3, GL11.GL_FLOAT, 12, vbo);
            assertEquals(4, GLStateManager.glGetInteger(GL14.GL_FOG_COORDINATE_ARRAY_STRIDE));
            assertEquals(vbo, GLStateManager.glGetInteger(GL15.GL_FOG_COORDINATE_ARRAY_BUFFER_BINDING));
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_COLOR_ARRAY));
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_NORMAL_ARRAY));
            assertTrue(colorArrayEnabled());

            assertEquals(3, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));
            assertEquals(GL11.GL_FLOAT, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE));
            assertEquals(12, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE));
            assertEquals(vbo, driverAttrib(position, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING));
            assertEquals(GL11.GL_TRUE, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            assertEquals(GL11.GL_TRUE, driverAttrib(color, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            assertEquals(GL11.GL_FALSE, driverAttrib(normal, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            assertEquals(GL11.GL_FALSE, driverAttrib(5, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());

            GLStateManager.glBindVertexArray(vaoB);
            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glDeleteVertexArrays(vaoB);
            GLStateManager.glPopClientAttrib();
            assertEquals(vao, GLStateManager.getBoundVAO(), "a deleted VAO must not be restored");
            assertVertexArray("after dead-vao pop", 3, GL11.GL_FLOAT, 12, vbo);
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            drainClientAttribStack();
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            for (final int cap : CLIENT_ARRAY_CAPS) {
                GLStateManager.glDisableClientState(cap);
            }
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glDeleteVertexArrays(vaoB);
            GLStateManager.glDeleteBuffers(vbo2);
            GLStateManager.glDeleteBuffers(ebo);
        }
    }

    @Test
    void enabledGenericAttributeWinsWithoutAPointer() {
        final int position = Usage.POSITION.getAttributeLocation();
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            assertFalse(VAOManager.get(position).genericPointer);

            GLStateManager.glEnableVertexAttribArray(position);
            assertTrue(VAOManager.get(position).genericPointer, "generic 0 supersedes position on its enable bit alone");
            GLStateManager.glDisableVertexAttribArray(position);
            assertFalse(VAOManager.get(position).genericPointer);
            assertEquals(3, VAOManager.get(position).size);
        } finally {
            GLStateManager.glDisableVertexAttribArray(position);
            GLStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void deletedBufferIsDetachedFromTheBoundVertexArray() {
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        drainErrors();
        GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, 4L);
        GLStateManager.glFogCoordPointer(GL11.GL_FLOAT, 4, 0L);
        assertEquals(vbo, GLStateManager.glGetInteger(GL15.GL_VERTEX_ARRAY_BUFFER_BINDING));

        GLStateManager.glDeleteBuffers(vbo);
        assertEquals(0, GLStateManager.glGetInteger(GL15.GL_VERTEX_ARRAY_BUFFER_BINDING));
        assertEquals(0, GLStateManager.glGetInteger(GL15.GL_FOG_COORDINATE_ARRAY_BUFFER_BINDING));
        assertEquals(12, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_STRIDE), "offset and stride survive the detach");
        assertEquals(3, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_SIZE));
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void shadowedPointerIsNotRepointedAtAReusedBufferName() {
        final int other = GLStateManager.glGenVertexArrays();
        final int conventional = GLStateManager.glGenBuffers();
        final int generic = GLStateManager.glGenBuffers();
        final int position = Usage.POSITION.getAttributeLocation();
        int reused = 0;
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, conventional);
            drainErrors();
            GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, generic);
            GLStateManager.glVertexAttribPointer(position, 2, GL11.GL_FLOAT, false, 8, 0L);
            GLStateManager.glEnableVertexAttribArray(position);

            GLStateManager.glBindVertexArray(other);
            GLStateManager.glDeleteBuffers(conventional);
            reused = GLStateManager.glGenBuffers();
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, reused);
            assumeTrue(reused == conventional, "driver did not reuse the buffer name");

            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glDisableVertexAttribArray(position);
            assertEquals(generic, driverAttrib(position, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING), "a pointer set before the delete must not attach to the new buffer");
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glDisableVertexAttribArray(position);
            GLStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glDeleteVertexArrays(other);
            GLStateManager.glDeleteBuffers(generic);
            if (reused != 0) GLStateManager.glDeleteBuffers(reused);
        }
    }

    @Test
    void popKeepsCurrentArraysWhenTheSavedArrayBufferWasDeleted() {
        final int vbo2 = GLStateManager.glGenBuffers();
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            drainErrors();
            GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo2);
            GLStateManager.glVertexPointer(2, GL11.GL_SHORT, 4, 0L);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glDeleteBuffers(vbo);
            GLStateManager.glPopClientAttrib();

            assertEquals(2, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_SIZE), "array state is not restored once its array buffer is gone");
            assertEquals(vbo2, GLStateManager.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING));
            assertEquals(GL13.GL_TEXTURE0, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            drainClientAttribStack();
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glDeleteBuffers(vbo2);
        }
    }

    @Test
    void clientAttribStackIsNotCompiledIntoDisplayLists() {
        final int other = GLStateManager.glGenVertexArrays();
        final int list = GLStateManager.glGenLists(1);
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glNewList(list, GL11.GL_COMPILE);
            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glPopClientAttrib();
            GLStateManager.glEndList();

            GLStateManager.glBindVertexArray(other);
            GLStateManager.glCallList(list);
            assertEquals(other, GLStateManager.getBoundVAO(), "replaying the list must not rebind the vertex array that was bound while compiling");
        } finally {
            drainClientAttribStack();
            GLStateManager.glDeleteLists(list, 1);
            GLStateManager.glDeleteVertexArrays(other);
        }
    }

    @Test
    void popRestoresDivisorAndStateWithoutAnAttributeLocation() {
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            drainErrors();
            GLStateManager.glVertexAttribPointer(5, 1, GL11.GL_FLOAT, false, 4, 0L);

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glPopClientAttrib();
            assertEquals(0, driverAttrib(5, GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR));

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glVertexAttribDivisor(5, 3);
            assertEquals(3, driverAttrib(5, GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR));
            GLStateManager.glPopClientAttrib();
            assertEquals(0, driverAttrib(5, GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR));

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glEnableClientState(GL14.GL_FOG_COORDINATE_ARRAY);
            GLStateManager.glPopClientAttrib();
            assertFalse(GLStateManager.glIsEnabled(GL14.GL_FOG_COORDINATE_ARRAY));

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glEnableVertexAttribArray(5);
            GLStateManager.glPopClientAttrib();
            assertEquals(GL11.GL_FALSE, driverAttrib(5, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            drainClientAttribStack();
            GLStateManager.glDisableVertexAttribArray(5);
            GLStateManager.glDisableClientState(GL14.GL_FOG_COORDINATE_ARRAY);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void interleavedArraysFromAClientBufferRegisterClientPointers() {
        final FloatBuffer data = BufferUtils.createFloatBuffer(64);
        final int position = Usage.POSITION.getAttributeLocation();
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            drainErrors();
            data.position(2);

            GLStateManager.glInterleavedArrays(GL11.GL_C4UB_V3F, 0, data);
            final long base = MemoryUtilities.memAddress(data);
            assertNotNull(VAOManager.get(position).clientPointer);
            assertEquals(base + 4, MemoryUtilities.memAddress(VAOManager.get(position).clientPointer));
            assertEquals(base + 4, MemoryUtilities.memAddress(GLStateManager.glGetPointer(GL11.GL_VERTEX_ARRAY_POINTER, 4)));
            assertEquals(base, MemoryUtilities.memAddress(GLStateManager.glGetPointer(GL11.GL_COLOR_ARRAY_POINTER, 4)));
            assertEquals(0, GLStateManager.glGetInteger(GL15.GL_VERTEX_ARRAY_BUFFER_BINDING));
            assertEquals(16, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_STRIDE));
            assertTrue(GLStateManager.ctx().vaos.hasAnyClientSideEnabledAttrib());
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            for (final int cap : CLIENT_ARRAY_CAPS) {
                GLStateManager.glDisableClientState(cap);
            }
        }
    }

    @Test
    void enableAndDisableRouteClientArrayCapsToClientState() {
        GLStateManager.glBindVertexArray(vao);
        drainErrors();
        for (final int cap : CLIENT_ARRAY_CAPS) {
            GLStateManager.glEnable(cap);
            assertTrue(GLStateManager.glIsEnabled(cap), hex(cap));
            GLStateManager.glDisable(cap);
            assertFalse(GLStateManager.glIsEnabled(cap), hex(cap));
        }
        GLStateManager.glEnable(GL11.GL_VERTEX_ARRAY);
        assertTrue(positionArrayEnabled());
        GLStateManager.glDisable(GL11.GL_VERTEX_ARRAY);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void illegalPointerArgumentsAreIgnored() {
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            drainErrors();
            GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLStateManager.glNormalPointer(GL11.GL_FLOAT, 12, 0L);

            GLStateManager.glVertexPointer(1, GL11.GL_FLOAT, 4, 0L);
            GLStateManager.glVertexPointer(3, GL11.GL_UNSIGNED_BYTE, 3, 0L);
            GLStateManager.glNormalPointer(GL11.GL_UNSIGNED_BYTE, 3, 0L);
            assertVertexArray("vertex after illegal calls", 3, GL11.GL_FLOAT, 12, vbo);
            assertEquals(GL11.GL_FLOAT, GLStateManager.glGetInteger(GL11.GL_NORMAL_ARRAY_TYPE));

            GLStateManager.glColorPointer(GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, 4, 0L);
            assertEquals(GL12.GL_BGRA, GLStateManager.glGetInteger(GL11.GL_COLOR_ARRAY_SIZE));
            GLStateManager.glColorPointer(GL12.GL_BGRA, GL11.GL_FLOAT, 16, 0L);
            assertEquals(GL11.GL_UNSIGNED_BYTE, GLStateManager.glGetInteger(GL11.GL_COLOR_ARRAY_TYPE));

            GLStateManager.glVertexAttribPointer(VAOManager.MAX_ATTRIBS, 3, GL11.GL_FLOAT, false, 0, 0L);
            GLStateManager.glEnableVertexAttribArray(VAOManager.MAX_ATTRIBS);
            GLStateManager.glDisableVertexAttribArray(VAOManager.MAX_ATTRIBS);
            GLStateManager.glVertexAttribPointer(5, 5, GL11.GL_FLOAT, false, 0, 0L);
            assertNull(VAOManager.get(5));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void edgeFlagIsCompiledIntoDisplayLists() {
        final int list = GLStateManager.glGenLists(1);
        try {
            GLStateManager.glEdgeFlag(true);
            GLStateManager.glNewList(list, GL11.GL_COMPILE);
            GLStateManager.glEdgeFlag(false);
            GLStateManager.glEndList();
            assertTrue(GLStateManager.glGetBoolean(GL11.GL_EDGE_FLAG), "compiling must not change the live edge flag");
            GLStateManager.glCallList(list);
            assertFalse(GLStateManager.glGetBoolean(GL11.GL_EDGE_FLAG));
        } finally {
            GLStateManager.glEdgeFlag(true);
            GLStateManager.glDeleteLists(list, 1);
        }
    }

    private static final int[] CLIENT_ARRAY_CAPS = {
        GL11.GL_VERTEX_ARRAY, GL11.GL_NORMAL_ARRAY, GL11.GL_COLOR_ARRAY, GL11.GL_TEXTURE_COORD_ARRAY,
        GL11.GL_INDEX_ARRAY, GL11.GL_EDGE_FLAG_ARRAY, GL14.GL_SECONDARY_COLOR_ARRAY, GL14.GL_FOG_COORDINATE_ARRAY
    };

    @Test
    void clientArrayEnablesAreAnsweredFromCache() {
        final int vaoB = GLStateManager.glGenVertexArrays();
        final int base = GLStateManager.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH);
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            drainErrors();
            for (final int cap : CLIENT_ARRAY_CAPS) {
                assertFalse(GLStateManager.glIsEnabled(cap), "default isEnabled " + hex(cap));
                assertEnableRoundTrip(cap, true);
            }

            GLStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY));
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY));
            assertFalse(GLStateManager.glGetBoolean(GL11.GL_TEXTURE_COORD_ARRAY));
            assertEquals(0, GLStateManager.glGetInteger(GL11.GL_TEXTURE_COORD_ARRAY));
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY));
            assertTrue(GLStateManager.glGetBoolean(GL11.GL_TEXTURE_COORD_ARRAY));
            assertEquals(1, GLStateManager.glGetInteger(GL11.GL_TEXTURE_COORD_ARRAY));
            GLStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

            GLStateManager.glEnableClientState(GL11.GL_INDEX_ARRAY);
            GLStateManager.glBindVertexArray(vaoB);
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_INDEX_ARRAY));
            assertFalse(GLStateManager.glGetBoolean(GL11.GL_INDEX_ARRAY));
            assertEquals(0, GLStateManager.glGetInteger(GL11.GL_INDEX_ARRAY));
            GLStateManager.glBindVertexArray(vao);
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_INDEX_ARRAY));
            assertTrue(GLStateManager.glGetBoolean(GL11.GL_INDEX_ARRAY));
            assertEquals(1, GLStateManager.glGetInteger(GL11.GL_INDEX_ARRAY));
            GLStateManager.glDisableClientState(GL11.GL_INDEX_ARRAY);

            GLStateManager.glBindVertexArray(vaoB);
            GLStateManager.glEnableVertexAttribArray(0);
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_VERTEX_ARRAY));
            assertFalse(GLStateManager.glGetBoolean(GL11.GL_VERTEX_ARRAY));
            assertEquals(0, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY));
            GLStateManager.glDisableVertexAttribArray(0);
            GLStateManager.glBindVertexArray(vao);

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glEnableClientState(GL14.GL_FOG_COORDINATE_ARRAY);
            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            assertTrue(GLStateManager.glIsEnabled(GL14.GL_FOG_COORDINATE_ARRAY));
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_VERTEX_ARRAY));
            GLStateManager.glPopClientAttrib();
            assertFalse(GLStateManager.glIsEnabled(GL14.GL_FOG_COORDINATE_ARRAY));
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_VERTEX_ARRAY));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            while (GLStateManager.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH) > base) {
                GLStateManager.glPopClientAttrib();
            }
            GLStateManager.glBindVertexArray(vaoB);
            GLStateManager.glDisableVertexAttribArray(0);
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            for (final int cap : CLIENT_ARRAY_CAPS) {
                GLStateManager.glDisableClientState(cap);
            }
            GLStateManager.glDeleteVertexArrays(vaoB);
        }
    }

    @Test
    void integerMatrixQueriesScaleLikeMesa() {
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        try {
            GLStateManager.glLoadIdentity();
            GLStateManager.glGetInteger(GL11.GL_MODELVIEW_MATRIX, ints);
            assertEquals(2147483647, ints.get(0));
            assertEquals(0, ints.get(1));
            assertEquals(2147483647, ints.get(15));
            assertEquals(2147483647, GLStateManager.glGetInteger(GL11.GL_MODELVIEW_MATRIX));
            assertEquals(1.0f, GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX));
        } finally {
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glPopMatrix();
        }
    }

    @Test
    void everyHintAndPointParameterAnswersEveryQueryTypeFromCache() {
        final int base = GLStateManager.getAttribDepth();
        final DoubleBuffer doubles = BufferUtils.createDoubleBuffer(16);
        GLStateManager.glPushAttrib(GL11.GL_HINT_BIT | GL11.GL_POINT_BIT);
        try {
            for (final int target : HINT_TARGETS) {
                GLStateManager.glHint(target, GL11.GL_NICEST);
                assertEquals(GL11.GL_NICEST, GLStateManager.glGetInteger(target), hex(target));
                assertEquals((float) GL11.GL_NICEST, GLStateManager.glGetFloat(target), hex(target));
                assertTrue(GLStateManager.glGetBoolean(target), hex(target));
            }
            GLStateManager.glPointParameteri(GL20.GL_POINT_SPRITE_COORD_ORIGIN, GL20.GL_LOWER_LEFT);
            assertEquals((float) GL20.GL_LOWER_LEFT, GLStateManager.glGetFloat(GL20.GL_POINT_SPRITE_COORD_ORIGIN));
            GLStateManager.glPointParameterf(GL14.GL_POINT_FADE_THRESHOLD_SIZE, 2.5f);
            assertEquals(3, GLStateManager.glGetInteger(GL14.GL_POINT_FADE_THRESHOLD_SIZE));
            assertTrue(GLStateManager.glGetBoolean(GL14.GL_POINT_FADE_THRESHOLD_SIZE));
            GLStateManager.glPointParameterf(GL14.GL_POINT_SIZE_MIN, Float.NaN);
            assertTrue(Float.isNaN(GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MIN)));

            final FloatBuffer attenuation = BufferUtils.createFloatBuffer(16);
            attenuation.put(0, 0.5f).put(1, 0.25f).put(2, 0.125f);
            GLStateManager.glPointParameter(GL14.GL_POINT_DISTANCE_ATTENUATION, attenuation);
            GLStateManager.glGetDouble(GL14.GL_POINT_DISTANCE_ATTENUATION, doubles);
            assertEquals(0.5, doubles.get(0));
            assertEquals(0.25, doubles.get(1));
            assertEquals(0.125, doubles.get(2));
        } finally {
            while (GLStateManager.getAttribDepth() > base) {
                GLStateManager.glPopAttrib();
            }
        }
    }

    @Test
    void textureBitRestoresPerUnitTextureEnables() {
        final int base = GLStateManager.getAttribDepth();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        final boolean cube = GLStateManager.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP);
        final boolean texture2d = GLStateManager.glIsEnabled(GL11.GL_TEXTURE_2D);
        try {
            GLStateManager.glPushAttrib(GL11.GL_TEXTURE_BIT);
            setEnabled(GL13.GL_TEXTURE_CUBE_MAP, !cube);
            setEnabled(GL11.GL_TEXTURE_2D, !texture2d);
            GLStateManager.glPopAttrib();
            assertEquals(cube, GLStateManager.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP));
            assertEquals(texture2d, GLStateManager.glIsEnabled(GL11.GL_TEXTURE_2D));
        } finally {
            while (GLStateManager.getAttribDepth() > base) {
                GLStateManager.glPopAttrib();
            }
            setEnabled(GL13.GL_TEXTURE_CUBE_MAP, cube);
            setEnabled(GL11.GL_TEXTURE_2D, texture2d);
        }
    }

    @Test
    void crossTypeQueriesConvert() {
        final FloatBuffer savedColor = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_CURRENT_COLOR, savedColor);
        final int savedFunc = GLStateManager.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        final float savedRef = GLStateManager.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        final float savedStart = GLStateManager.glGetFloat(GL11.GL_FOG_START);
        final boolean savedLighting = GLStateManager.glIsEnabled(GL11.GL_LIGHTING);
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        try {
            drainErrors();
            GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5f);
            assertEquals(1073741823, GLStateManager.glGetInteger(GL11.GL_ALPHA_TEST_REF));

            assertEquals((float) GLStateManager.glGetInteger(GL11.GL_FOG_MODE), GLStateManager.glGetFloat(GL11.GL_FOG_MODE));

            GLStateManager.glFogf(GL11.GL_FOG_START, -1.5f);
            assertEquals(-2, GLStateManager.glGetInteger(GL11.GL_FOG_START));

            GLStateManager.glEnable(GL11.GL_LIGHTING);
            assertEquals(1, GLStateManager.glGetInteger(GL11.GL_LIGHTING));
            GLStateManager.glDisable(GL11.GL_LIGHTING);
            assertEquals(0, GLStateManager.glGetInteger(GL11.GL_LIGHTING));

            GLStateManager.glColor4f(1f, 0.5f, 0f, 1f);
            ints.clear();
            GLStateManager.glGetInteger(GL11.GL_CURRENT_COLOR, ints);
            assertEquals(2147483647, ints.get(0));
            assertEquals(1073741823, ints.get(1));
            assertEquals(0, ints.get(2));
            assertEquals(2147483647, ints.get(3));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glFogf(GL11.GL_FOG_START, savedStart);
            GLStateManager.glColor4f(savedColor.get(0), savedColor.get(1), savedColor.get(2), savedColor.get(3));
            GLStateManager.glAlphaFunc(savedFunc, savedRef);
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

    private static final int[] HINT_TARGETS = {
        GL11.GL_PERSPECTIVE_CORRECTION_HINT, GL11.GL_POINT_SMOOTH_HINT, GL11.GL_LINE_SMOOTH_HINT, GL11.GL_POLYGON_SMOOTH_HINT,
        GL11.GL_FOG_HINT, GL13.GL_TEXTURE_COMPRESSION_HINT, GL14.GL_GENERATE_MIPMAP_HINT, GL20.GL_FRAGMENT_SHADER_DERIVATIVE_HINT
    };

    @Test
    void queriesWriteAtBufferPosition() {
        final IntBuffer ints = BufferUtils.createIntBuffer(32);
        final FloatBuffer floats = BufferUtils.createFloatBuffer(32);
        final DoubleBuffer doubles = BufferUtils.createDoubleBuffer(32);
        final FloatBuffer reference = BufferUtils.createFloatBuffer(32);
        final FloatBuffer savedColor = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_CURRENT_COLOR, savedColor);
        final float savedStart = GLStateManager.glGetFloat(GL11.GL_FOG_START);
        try {
            drainErrors();
            for (int i = 0; i < 32; i++) {
                ints.put(i, -777);
                floats.put(i, -777f);
                doubles.put(i, -777.0);
            }

            ints.position(4);
            GLStateManager.glGetInteger(GL11.GL_MATRIX_MODE, ints);
            assertEquals(GLStateManager.glGetInteger(GL11.GL_MATRIX_MODE), ints.get(4));
            assertEquals(-777, ints.get(0));

            floats.position(4);
            GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, floats);
            reference.clear();
            GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, reference);
            for (int i = 0; i < 16; i++) {
                assertEquals(reference.get(i), floats.get(4 + i), "modelview element " + i);
            }
            assertEquals(-777f, floats.get(0));

            for (int i = 0; i < 32; i++) {
                ints.put(i, -777);
            }
            GLStateManager.glColor4f(0.25f, 0.5f, 0.75f, 1f);
            ints.position(4);
            GLStateManager.glGetInteger(GL11.GL_CURRENT_COLOR, ints);
            assertEquals(536870911, ints.get(4));
            assertEquals(1073741823, ints.get(5));
            assertEquals(1610612735, ints.get(6));
            assertEquals(2147483647, ints.get(7));
            assertEquals(-777, ints.get(0));

            GLStateManager.glFogf(GL11.GL_FOG_START, 3.5f);
            doubles.position(4);
            GLStateManager.glGetDouble(GL11.GL_FOG_START, doubles);
            assertEquals(3.5, doubles.get(4));
            assertEquals(-777.0, doubles.get(0));

            for (int i = 0; i < 32; i++) {
                floats.put(i, -777f);
            }
            floats.position(4);
            GLStateManager.glGetMaterial(GL11.GL_FRONT, GL11.GL_DIFFUSE, floats);
            reference.clear();
            GLStateManager.glGetMaterial(GL11.GL_FRONT, GL11.GL_DIFFUSE, reference);
            for (int i = 0; i < 4; i++) {
                assertEquals(reference.get(i), floats.get(4 + i), "diffuse element " + i);
            }
            assertEquals(-777f, floats.get(0));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glFogf(GL11.GL_FOG_START, savedStart);
            GLStateManager.glColor4f(savedColor.get(0), savedColor.get(1), savedColor.get(2), savedColor.get(3));
        }
    }

    @Test
    void hintsRoundTripAndFollowHintBit() {
        final int base = GLStateManager.getAttribDepth();
        final int[] saved = new int[HINT_TARGETS.length];
        for (int i = 0; i < HINT_TARGETS.length; i++) {
            saved[i] = GLStateManager.glGetInteger(HINT_TARGETS[i]);
        }
        try {
            drainErrors();
            for (final int target : HINT_TARGETS) {
                final String name = hex(target);
                assertEquals(GL11.GL_DONT_CARE, GLStateManager.glGetInteger(target), "default " + name);
                GLStateManager.glHint(target, GL11.GL_NICEST);
                assertEquals(GL11.GL_NICEST, GLStateManager.glGetInteger(target), "nicest " + name);
                GLStateManager.glHint(target, 12345);
                assertEquals(GL11.GL_NICEST, GLStateManager.glGetInteger(target), "invalid mode " + name);
            }
            GLStateManager.glPushAttrib(GL11.GL_HINT_BIT);
            for (final int target : HINT_TARGETS) {
                GLStateManager.glHint(target, GL11.GL_FASTEST);
                assertEquals(GL11.GL_FASTEST, GLStateManager.glGetInteger(target), "fastest " + hex(target));
            }
            GLStateManager.glPopAttrib();
            for (final int target : HINT_TARGETS) {
                assertEquals(GL11.GL_NICEST, GLStateManager.glGetInteger(target), "restored " + hex(target));
            }
            assertEquals(GL11.GL_RENDER, GLStateManager.glGetInteger(GL11.GL_RENDER_MODE));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            while (GLStateManager.getAttribDepth() > base) {
                GLStateManager.glPopAttrib();
            }
            for (int i = 0; i < HINT_TARGETS.length; i++) {
                GLStateManager.glHint(HINT_TARGETS[i], saved[i]);
            }
        }
    }

    @Test
    void pointParametersRoundTripAndFollowPointBit() {
        final int base = GLStateManager.getAttribDepth();
        final FloatBuffer attenuation = BufferUtils.createFloatBuffer(16);
        final FloatBuffer savedAttenuation = BufferUtils.createFloatBuffer(16);
        final float savedMin = GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MIN);
        final float savedMax = GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MAX);
        final float savedFade = GLStateManager.glGetFloat(GL14.GL_POINT_FADE_THRESHOLD_SIZE);
        final int savedOrigin = GLStateManager.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN);
        GLStateManager.glGetFloat(GL14.GL_POINT_DISTANCE_ATTENUATION, savedAttenuation);
        GLStateManager.glPushAttrib(GL11.GL_POINT_BIT);
        try {
            drainErrors();
            assertEquals(0f, savedMin);
            assertEquals(driverMaxPointSize(), savedMax);
            assertEquals(1f, savedFade);
            assertEquals(1f, savedAttenuation.get(0));
            assertEquals(0f, savedAttenuation.get(1));
            assertEquals(0f, savedAttenuation.get(2));
            assertEquals(GL20.GL_UPPER_LEFT, savedOrigin);

            GLStateManager.glPointParameterf(GL14.GL_POINT_SIZE_MIN, 0.5f);
            GLStateManager.glPointParameterf(GL14.GL_POINT_SIZE_MAX, 2.5f);
            GLStateManager.glPointParameterf(GL14.GL_POINT_FADE_THRESHOLD_SIZE, 4.5f);
            assertEquals(0.5f, GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MIN));
            assertEquals(2.5f, GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MAX));
            assertEquals(4.5f, GLStateManager.glGetFloat(GL14.GL_POINT_FADE_THRESHOLD_SIZE));
            GLStateManager.glPointParameterf(GL14.GL_POINT_SIZE_MIN, -1f);
            assertEquals(0.5f, GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MIN));

            attenuation.put(0, 0.5f).put(1, 0.25f).put(2, 0.125f);
            attenuation.position(0).limit(3);
            GLStateManager.glPointParameter(GL14.GL_POINT_DISTANCE_ATTENUATION, attenuation);
            attenuation.clear();
            for (int i = 0; i < 16; i++) {
                attenuation.put(i, -777f);
            }
            GLStateManager.glGetFloat(GL14.GL_POINT_DISTANCE_ATTENUATION, attenuation);
            assertEquals(0.5f, attenuation.get(0));
            assertEquals(0.25f, attenuation.get(1));
            assertEquals(0.125f, attenuation.get(2));

            GLStateManager.glPointParameteri(GL20.GL_POINT_SPRITE_COORD_ORIGIN, GL20.GL_LOWER_LEFT);
            assertEquals(GL20.GL_LOWER_LEFT, GLStateManager.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN));

            GLStateManager.glPushAttrib(GL11.GL_POINT_BIT);
            GLStateManager.glPointParameterf(GL14.GL_POINT_SIZE_MIN, 1f);
            GLStateManager.glPointParameterf(GL14.GL_POINT_SIZE_MAX, 3f);
            GLStateManager.glPointParameterf(GL14.GL_POINT_FADE_THRESHOLD_SIZE, 2f);
            attenuation.put(0, 1f).put(1, 1f).put(2, 1f);
            attenuation.position(0).limit(3);
            GLStateManager.glPointParameter(GL14.GL_POINT_DISTANCE_ATTENUATION, attenuation);
            GLStateManager.glPointParameteri(GL20.GL_POINT_SPRITE_COORD_ORIGIN, GL20.GL_UPPER_LEFT);
            assertEquals(1f, GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MIN));
            assertEquals(GL20.GL_UPPER_LEFT, GLStateManager.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN));
            GLStateManager.glPopAttrib();

            assertEquals(0.5f, GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MIN));
            assertEquals(2.5f, GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MAX));
            assertEquals(4.5f, GLStateManager.glGetFloat(GL14.GL_POINT_FADE_THRESHOLD_SIZE));
            assertEquals(GL20.GL_LOWER_LEFT, GLStateManager.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN));
            attenuation.clear();
            GLStateManager.glGetFloat(GL14.GL_POINT_DISTANCE_ATTENUATION, attenuation);
            assertEquals(0.5f, attenuation.get(0));
            assertEquals(0.25f, attenuation.get(1));
            assertEquals(0.125f, attenuation.get(2));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            while (GLStateManager.getAttribDepth() > base) {
                GLStateManager.glPopAttrib();
            }
            assertEquals(savedMax, GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MAX));
        }
    }

    private static void assertEnableRoundTrip(int cap, boolean clientState) {
        final String name = hex(cap);
        if (clientState) {
            GLStateManager.glEnableClientState(cap);
        } else {
            GLStateManager.glEnable(cap);
        }
        assertTrue(GLStateManager.glIsEnabled(cap), "enabled isEnabled " + name);
        assertTrue(GLStateManager.glGetBoolean(cap), "enabled getBoolean " + name);
        assertEquals(1, GLStateManager.glGetInteger(cap), "enabled getInteger " + name);
        if (clientState) {
            GLStateManager.glDisableClientState(cap);
        } else {
            GLStateManager.glDisable(cap);
        }
        assertFalse(GLStateManager.glIsEnabled(cap), "disabled isEnabled " + name);
        assertFalse(GLStateManager.glGetBoolean(cap), "disabled getBoolean " + name);
        assertEquals(0, GLStateManager.glGetInteger(cap), "disabled getInteger " + name);
    }

    @Test
    void cacheOnlyEnablesRoundTrip() {
        final int base = GLStateManager.getAttribDepth();
        final boolean savedSprite = GLStateManager.glIsEnabled(GL20.GL_POINT_SPRITE);
        final boolean savedTwoSide = GLStateManager.glIsEnabled(GL20.GL_VERTEX_PROGRAM_TWO_SIDE);
        final boolean savedEdge = GLStateManager.glGetBoolean(GL11.GL_EDGE_FLAG);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        final boolean savedCube0 = GLStateManager.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        final boolean savedCube1 = GLStateManager.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        try {
            drainErrors();
            assertEnableRoundTrip(GL20.GL_POINT_SPRITE, false);
            assertEnableRoundTrip(GL20.GL_VERTEX_PROGRAM_TWO_SIDE, false);
            assertEnableRoundTrip(GL13.GL_TEXTURE_CUBE_MAP, false);

            GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLStateManager.glEnable(GL20.GL_POINT_SPRITE);
            GLStateManager.glEnable(GL20.GL_VERTEX_PROGRAM_TWO_SIDE);
            GLStateManager.glEnable(GL13.GL_TEXTURE_CUBE_MAP);
            assertTrue(GLStateManager.glIsEnabled(GL20.GL_POINT_SPRITE));
            assertTrue(GLStateManager.glIsEnabled(GL20.GL_VERTEX_PROGRAM_TWO_SIDE));
            assertTrue(GLStateManager.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP));
            GLStateManager.glPopAttrib();
            assertFalse(GLStateManager.glIsEnabled(GL20.GL_POINT_SPRITE));
            assertFalse(GLStateManager.glIsEnabled(GL20.GL_VERTEX_PROGRAM_TWO_SIDE));
            assertFalse(GLStateManager.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP));

            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glDisable(GL13.GL_TEXTURE_CUBE_MAP);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glEnable(GL13.GL_TEXTURE_CUBE_MAP);
            assertTrue(GLStateManager.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP));
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            assertFalse(GLStateManager.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP));
            assertFalse(GLStateManager.glGetBoolean(GL13.GL_TEXTURE_CUBE_MAP));
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            assertTrue(GLStateManager.glGetBoolean(GL13.GL_TEXTURE_CUBE_MAP));
            GLStateManager.glDisable(GL13.GL_TEXTURE_CUBE_MAP);

            assertTrue(savedEdge);
            GLStateManager.glEdgeFlag(false);
            assertFalse(GLStateManager.glGetBoolean(GL11.GL_EDGE_FLAG));
            GLStateManager.glPushAttrib(GL11.GL_CURRENT_BIT);
            GLStateManager.glEdgeFlag(true);
            assertTrue(GLStateManager.glGetBoolean(GL11.GL_EDGE_FLAG));
            GLStateManager.glPopAttrib();
            assertFalse(GLStateManager.glGetBoolean(GL11.GL_EDGE_FLAG));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            while (GLStateManager.getAttribDepth() > base) {
                GLStateManager.glPopAttrib();
            }
            GLStateManager.glEdgeFlag(savedEdge);
            setEnabled(GL20.GL_POINT_SPRITE, savedSprite);
            setEnabled(GL20.GL_VERTEX_PROGRAM_TWO_SIDE, savedTwoSide);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            setEnabled(GL13.GL_TEXTURE_CUBE_MAP, savedCube1);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            setEnabled(GL13.GL_TEXTURE_CUBE_MAP, savedCube0);
        }
    }
}
