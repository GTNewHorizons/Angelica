package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.recording.commands.BatchedIndexedDrawCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawBatch;
import com.gtnewhorizons.angelica.glsm.recording.support.DisplayListTestFixture;
import com.gtnewhorizons.angelica.glsm.states.VertexAttribState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexedDrawBatchBuilderTest extends DisplayListTestFixture {

    @Test
    void singleDraw_bakedSurvivesVboMutation() {
        final int vbo = defaultQuadVbo();
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 2, 3, 0);
        final int vao = setupPosColorVao(vbo);
        final int list = recordSingleIndexedDraw(vao, ebo, GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_SHORT);

        final BatchedIndexedDrawCmd cmd = firstBatchedCmd(list);
        assertNotNull(cmd);
        assertEquals(GL11.GL_TRIANGLES, cmd.getDrawMode());
        assertEquals(6, cmd.getIndexCount());
        assertEquals(GL11.GL_UNSIGNED_INT, cmd.getIndexType(), "batched indices always widen to GL_UNSIGNED_INT");

        // Overwrite source VBO with garbage — baked batch must be unaffected.
        overwriteBufferWithGarbage(GL15.GL_ARRAY_BUFFER, vbo, 4 * POS_COLOR_STRIDE);

        final ByteBuffer read = readBufferBytes(GL15.GL_ARRAY_BUFFER, firstBatch(list).getSharedVBO(), 4 * POS_COLOR_STRIDE);
        for (int i = 0; i < 4; i++) {
            assertEquals(DEFAULT_QUAD_POSITIONS[i * 2], read.getFloat(i * POS_COLOR_STRIDE), 0f, "pos.x v=" + i);
            assertEquals(DEFAULT_QUAD_POSITIONS[i * 2 + 1], read.getFloat(i * POS_COLOR_STRIDE + 4), 0f, "pos.y v=" + i);
            for (int c = 0; c < 4; c++) {
                assertEquals(DEFAULT_SOLID_COLORS[i][c], read.get(i * POS_COLOR_STRIDE + 8 + c), "color v=" + i + " c=" + c);
            }
        }
        MemoryUtilities.memFree(read);
    }

    @Test
    void compileAndExecute_firesLiveAndBakes() {
        final int vbo = defaultQuadVbo();
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2);
        final int vao = setupPosColorVao(vbo);

        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glEndList();

        final BatchedIndexedDrawCmd cmd = firstBatchedCmd(list);
        assertNotNull(cmd);
        assertEquals(3, cmd.getIndexCount());
    }

    @Test
    void quads_triangulateAtRecord() {
        final int vbo = defaultQuadVbo();
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 3);
        final int vao = setupPosColorVao(vbo);
        final int list = recordSingleIndexedDraw(vao, ebo, GL11.GL_QUADS, 4, GL11.GL_UNSIGNED_SHORT);

        final BatchedIndexedDrawCmd cmd = firstBatchedCmd(list);
        assertNotNull(cmd);
        assertEquals(GL11.GL_TRIANGLES, cmd.getDrawMode());
        assertEquals(6, cmd.getIndexCount());

        // EBO is widened to UNSIGNED_INT and uses GL_LAST_VERTEX_CONVENTION:
        // (a,b,d,b,c,d) — v3 is the provoking vertex for both triangles, diagonal b→d.
        final ByteBuffer ib = readBufferBytes(GL15.GL_ELEMENT_ARRAY_BUFFER, firstBatch(list).getSharedEBO(), 6 * 4);
        assertUintIndices(ib, 0, 1, 3, 1, 2, 3);
        MemoryUtilities.memFree(ib);
    }

    @Test
    void offsetAndMinMaxRebase() {
        // 8-vertex VBO; draw references only vertices 5..7 via a 6L byte offset into the EBO.
        final float[] positions = new float[16];
        final byte[][] colors = new byte[8][4];
        for (int i = 0; i < 8; i++) {
            positions[i * 2] = i;
            positions[i * 2 + 1] = i * 2;
            colors[i][0] = (byte) i;
            colors[i][1] = (byte) (i + 1);
            colors[i][2] = (byte) (i + 2);
            colors[i][3] = (byte) (i + 3);
        }
        final int vbo = uploadPosColorVbo(positions, colors);
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2,   5, 6, 7);
        final int vao = setupPosColorVao(vbo);

        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT, 6L);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glEndList();

        assertEquals(1, allBatchedCmds(list).size());

        final IndexedDrawBatch batch = firstBatch(list);
        final ByteBuffer ib = readBufferBytes(GL15.GL_ELEMENT_ARRAY_BUFFER, batch.getSharedEBO(), 3 * 4);
        // Indices rebased against the tight [minVtx..maxVtx] range.
        assertUintIndices(ib, 0, 1, 2);
        MemoryUtilities.memFree(ib);

        final ByteBuffer vboRead = readBufferBytes(GL15.GL_ARRAY_BUFFER, batch.getSharedVBO(), 3 * POS_COLOR_STRIDE);
        for (int i = 0; i < 3; i++) {
            final int orig = 5 + i;
            assertEquals(positions[orig * 2], vboRead.getFloat(i * POS_COLOR_STRIDE), 0f, "pos.x v=" + orig);
            assertEquals(positions[orig * 2 + 1], vboRead.getFloat(i * POS_COLOR_STRIDE + 4), 0f, "pos.y v=" + orig);
            for (int c = 0; c < 4; c++) {
                assertEquals(colors[orig][c], vboRead.get(i * POS_COLOR_STRIDE + 8 + c), "col v=" + orig + " c=" + c);
            }
        }
        MemoryUtilities.memFree(vboRead);
    }

    @ParameterizedTest(name = "indexType=0x{0}")
    @ValueSource(ints = {GL11.GL_UNSIGNED_BYTE, GL11.GL_UNSIGNED_SHORT, GL11.GL_UNSIGNED_INT})
    void anyIndexType_widensToUnsignedInt(int indexType) {
        final int vbo = defaultQuadVbo();
        final int ebo = uploadEbo(indexType, 0, 1, 2, 2, 3, 0);
        final int vao = setupPosColorVao(vbo);
        final int list = recordSingleIndexedDraw(vao, ebo, GL11.GL_TRIANGLES, 6, indexType);

        final BatchedIndexedDrawCmd cmd = firstBatchedCmd(list);
        assertNotNull(cmd);
        assertEquals(6, cmd.getIndexCount());
        assertEquals(GL11.GL_UNSIGNED_INT, cmd.getIndexType(), "batching always widens indices to UINT for safe base-vertex shifting");
    }

    @Test
    void emptyRecorder_producesNoBatches() {
        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glEndList();

        assertTrue(batchesOf(list).isEmpty(), "recorder with no draws produces no batches");
        assertTrue(allBatchedCmds(list).isEmpty(), "no placeholders either");
    }

    @Test
    void noEboBoundSkipsAndDoesNotBake() {
        final int vbo = defaultQuadVbo();
        final int vao = setupPosColorVao(vbo);

        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glEndList();

        assertTrue(allBatchedCmds(list).isEmpty(), "no EBO bound → no batched command attached");
        assertTrue(batchesOf(list).isEmpty(), "no batches either");
    }

    @Test
    void customLoc3FloatAttribRoundtrips() {
        // 4-attrib layout: pos(2f) | uv(2f) | col(4ub) | texBounds(4f) = 36 bytes/vertex.
        final int STRIDE = 36;
        final int N = 4;
        final float[][] pos = {{0f,0f},{1f,0f},{1f,1f},{0f,1f}};
        final float[][] uv  = {{0.1f,0.2f},{0.3f,0.4f},{0.5f,0.6f},{0.7f,0.8f}};
        final byte[][] col  = {{10,20,30,40},{50,60,70,(byte)80},{(byte)90,(byte)100,(byte)110,(byte)120},{(byte)130,(byte)140,(byte)150,(byte)160}};
        final float[][] tb  = {{1f,2f,3f,4f},{5f,6f,7f,8f},{9f,10f,11f,12f},{13f,14f,15f,16f}};
        final int vbo = newBuffer();
        final ByteBuffer data = MemoryUtilities.memAlloc(N * STRIDE).order(ByteOrder.nativeOrder());
        for (int i = 0; i < N; i++) {
            data.putFloat(pos[i][0]); data.putFloat(pos[i][1]);
            data.putFloat(uv[i][0]);  data.putFloat(uv[i][1]);
            data.put(col[i][0]); data.put(col[i][1]); data.put(col[i][2]); data.put(col[i][3]);
            data.putFloat(tb[i][0]); data.putFloat(tb[i][1]); data.putFloat(tb[i][2]); data.putFloat(tb[i][3]);
        }
        data.flip();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        MemoryUtilities.memFree(data);

        final int vao = newVao();
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLStateManager.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, STRIDE, 0);
        GLStateManager.glEnableVertexAttribArray(0);
        GLStateManager.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, STRIDE, 8);
        GLStateManager.glEnableVertexAttribArray(1);
        GLStateManager.glVertexAttribPointer(2, 4, GL11.GL_UNSIGNED_BYTE, true, STRIDE, 16);
        GLStateManager.glEnableVertexAttribArray(2);
        GLStateManager.glVertexAttribPointer(3, 4, GL11.GL_FLOAT, false, STRIDE, 20);
        GLStateManager.glEnableVertexAttribArray(3);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GLStateManager.glBindVertexArray(0);

        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 2, 3, 0);
        final int list = recordSingleIndexedDraw(vao, ebo, GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_SHORT);

        assertNotNull(firstBatchedCmd(list), "loc3 attrib should not cause the baker to skip");

        // Baked layout is tight interleaved in ascending-location order — same stride as source.
        final ByteBuffer read = readBufferBytes(GL15.GL_ARRAY_BUFFER, firstBatch(list).getSharedVBO(), N * STRIDE);
        for (int i = 0; i < N; i++) {
            final int base = i * STRIDE;
            assertEquals(pos[i][0], read.getFloat(base), 0f, "pos.x v=" + i);
            assertEquals(pos[i][1], read.getFloat(base + 4), 0f, "pos.y v=" + i);
            assertEquals(uv[i][0],  read.getFloat(base + 8), 0f, "uv.s v=" + i);
            assertEquals(uv[i][1],  read.getFloat(base + 12), 0f, "uv.t v=" + i);
            for (int c = 0; c < 4; c++) {
                assertEquals(col[i][c], read.get(base + 16 + c), "col v=" + i + " c=" + c);
            }
            for (int c = 0; c < 4; c++) {
                assertEquals(tb[i][c], read.getFloat(base + 20 + c * 4), 0f, "tb v=" + i + " c=" + c);
            }
        }
        MemoryUtilities.memFree(read);
    }

    @Test
    void glDeleteLists_freesBatchedObjects() {
        final int vbo = defaultQuadVbo();
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2);
        final int vao = setupPosColorVao(vbo);
        final int list = recordSingleIndexedDraw(vao, ebo, GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT);

        final IndexedDrawBatch batch = firstBatch(list);
        final int sharedVAO = batch.getSharedVAO();
        final int sharedVBO = batch.getSharedVBO();
        final int sharedEBO = batch.getSharedEBO();
        assertTrue(GL15.glIsBuffer(sharedVBO), "shared VBO live");
        assertTrue(GL15.glIsBuffer(sharedEBO), "shared EBO live");
        assertTrue(GL30.glIsVertexArray(sharedVAO), "shared VAO live");

        GLStateManager.glDeleteLists(list, 1);
        untrackList(list);

        assertFalse(GL15.glIsBuffer(sharedVBO), "shared VBO freed");
        assertFalse(GL15.glIsBuffer(sharedEBO), "shared EBO freed");
        assertFalse(GL30.glIsVertexArray(sharedVAO), "shared VAO freed");
    }

    @Test
    void reRecord_freesOldBatchedObjects() {
        final int vbo = defaultQuadVbo();
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2);
        final int vao = setupPosColorVao(vbo);
        final int list = recordSingleIndexedDraw(vao, ebo, GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT);

        final IndexedDrawBatch first = firstBatch(list);
        final int oldVAO = first.getSharedVAO();
        final int oldVBO = first.getSharedVBO();
        final int oldEBO = first.getSharedEBO();

        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glEndList();

        assertFalse(GL15.glIsBuffer(oldVBO), "old shared VBO freed on re-record");
        assertFalse(GL15.glIsBuffer(oldEBO), "old shared EBO freed on re-record");
        assertFalse(GL30.glIsVertexArray(oldVAO), "old shared VAO freed on re-record");
    }

    @Test
    void clientPointerAttribsBakeIntoSharedVbo() {
        final int N = 4;
        final ByteBuffer clientData = BufferUtils.createByteBuffer(N * POS_COLOR_STRIDE).order(ByteOrder.nativeOrder());
        final float[][] pos = {{0f,0f},{1f,0f},{1f,1f},{0f,1f}};
        final byte[][] col  = {{(byte)0xAA,(byte)0xBB,(byte)0xCC,(byte)0xDD},
                               {(byte)0x11,(byte)0x22,(byte)0x33,(byte)0x44},
                               {(byte)0x55,(byte)0x66,(byte)0x77,(byte)0x88},
                               {(byte)0x99,(byte)0x00,(byte)0x0F,(byte)0xF0}};
        for (int i = 0; i < N; i++) {
            clientData.putFloat(pos[i][0]); clientData.putFloat(pos[i][1]);
            clientData.put(col[i][0]); clientData.put(col[i][1]);
            clientData.put(col[i][2]); clientData.put(col[i][3]);
        }
        clientData.flip();

        final int vao = newVao();
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        final ByteBuffer colorSlice = clientData.duplicate().order(ByteOrder.nativeOrder());
        colorSlice.position(8);
        VertexAttribState.set(0, 2, GL11.GL_FLOAT, false, POS_COLOR_STRIDE, clientData, 0);
        VertexAttribState.setEnabled(0, true);
        VertexAttribState.set(1, 4, GL11.GL_UNSIGNED_BYTE, true, POS_COLOR_STRIDE, colorSlice, 0);
        VertexAttribState.setEnabled(1, true);
        GLStateManager.glBindVertexArray(0);

        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2);
        final int list = recordSingleIndexedDraw(vao, ebo, GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT);

        assertNotNull(firstBatchedCmd(list));

        // Tight interleaved baked layout for 3 drawn vertices = 36 bytes total.
        final ByteBuffer owned = readBufferBytes(GL15.GL_ARRAY_BUFFER, firstBatch(list).getSharedVBO(), 3 * POS_COLOR_STRIDE);
        for (int i = 0; i < 3; i++) {
            assertEquals(pos[i][0], owned.getFloat(i * POS_COLOR_STRIDE), 0f, "loc0 pos.x v=" + i);
            assertEquals(pos[i][1], owned.getFloat(i * POS_COLOR_STRIDE + 4), 0f, "loc0 pos.y v=" + i);
            for (int c = 0; c < 4; c++) {
                assertEquals(col[i][c], owned.get(i * POS_COLOR_STRIDE + 8 + c), "loc1 color v=" + i + " c=" + c);
            }
        }
        MemoryUtilities.memFree(owned);
    }

    @Test
    void multiDrawSameFormat_mergesIntoSingleBatch() {
        final int vbo = defaultQuadVbo();
        final int eboA = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2);
        final int eboB = uploadEbo(GL11.GL_UNSIGNED_SHORT, 2, 3, 0);
        final int vao = setupPosColorVao(vbo);

        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboA);
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboB);
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glEndList();

        final List<BatchedIndexedDrawCmd> cmds = allBatchedCmds(list);
        assertEquals(2, cmds.size(), "two indexed draws → two batched commands");
        final List<IndexedDrawBatch> batches = batchesOf(list);
        assertEquals(1, batches.size(), "same layout → one shared batch");

        assertEquals(cmds.get(0).getSharedVAO(), cmds.get(1).getSharedVAO(), "shared VAO across draws in the same batch");
        assertEquals(batches.getFirst().getSharedVAO(), cmds.get(0).getSharedVAO());

        assertEquals(0L, cmds.get(0).getIndexByteOffset());
        assertEquals(3 * 4L, cmds.get(1).getIndexByteOffset(), "second draw offset = first.indexCount * sizeof(UINT)");

        // Shared EBO holds concatenated + rebased UINT indices. First draw minVtx=0 → [0,1,2];
        // second draw minVtx=0, vertexBase=3 → [2+3, 3+3, 0+3] = [5, 6, 3].
        final ByteBuffer ib = readBufferBytes(GL15.GL_ELEMENT_ARRAY_BUFFER, batches.getFirst().getSharedEBO(), 6 * 4);
        assertUintIndices(ib, 0, 1, 2, 5, 6, 3);
        MemoryUtilities.memFree(ib);
    }

    @Test
    void multiDrawDifferentFormats_splitBatches() {
        // Draw A: pos+color (stride=12). Draw B: pos-only (stride=8). Different layout → two batches.
        final int vboA = defaultQuadVbo();
        final int vaoA = setupPosColorVao(vboA);

        final int vboB = newBuffer();
        final ByteBuffer bData = MemoryUtilities.memAlloc(4 * 8).order(ByteOrder.nativeOrder());
        for (int i = 0; i < 4; i++) {
            bData.putFloat(DEFAULT_QUAD_POSITIONS[i * 2]);
            bData.putFloat(DEFAULT_QUAD_POSITIONS[i * 2 + 1]);
        }
        bData.flip();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboB);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, bData, GL15.GL_STATIC_DRAW);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        MemoryUtilities.memFree(bData);

        final int vaoB = newVao();
        GLStateManager.glBindVertexArray(vaoB);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboB);
        GLStateManager.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 8, 0);
        GLStateManager.glEnableVertexAttribArray(0);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GLStateManager.glBindVertexArray(0);

        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2);

        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glBindVertexArray(vaoA);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glBindVertexArray(vaoB);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glEndList();

        final List<BatchedIndexedDrawCmd> cmds = allBatchedCmds(list);
        assertEquals(2, cmds.size());
        assertEquals(2, batchesOf(list).size(), "incompatible layouts → two batches");
        assertNotEquals(cmds.get(0).getSharedVAO(), cmds.get(1).getSharedVAO(), "VAOs from separate batches must be distinct");
    }

    @Test
    void sameSizesDifferentLocations_splitIntoSeparateBatches() {
        // Both VAOs: pos 2f + color 4ub (same sizes/types/normalized). Only the enabled
        // location sets differ — VAO-A uses {0,1}, VAO-B uses {0,2}. If AttribLayoutKey
        // forgot about locations they'd merge and VAO-B's color data would get wired to
        // loc 1 instead of loc 2.
        final int vbo = defaultQuadVbo();

        final int vaoA = setupPosColorVao(vbo);

        final int vaoB = newVao();
        GLStateManager.glBindVertexArray(vaoB);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLStateManager.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, POS_COLOR_STRIDE, 0);
        GLStateManager.glEnableVertexAttribArray(0);
        GLStateManager.glVertexAttribPointer(2, 4, GL11.GL_UNSIGNED_BYTE, true, POS_COLOR_STRIDE, 8);
        GLStateManager.glEnableVertexAttribArray(2);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GLStateManager.glBindVertexArray(0);

        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2);

        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glBindVertexArray(vaoA);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glBindVertexArray(vaoB);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glEndList();

        final List<BatchedIndexedDrawCmd> cmds = allBatchedCmds(list);
        assertEquals(2, cmds.size());
        assertEquals(2, batchesOf(list).size(), "different enabled locations → two batches");
        assertNotEquals(cmds.get(0).getSharedVAO(), cmds.get(1).getSharedVAO(), "VAOs from separate batches must be distinct");
    }

    @Test
    void nullListProbeCompiles() {
        assertNull(DisplayListManager.getDisplayList(0));
    }

    @Test
    void multiLayoutDraws_emitInInsertionOrder() {
        // Two distinct layouts. The builder uses a LinkedHashMap, so the first
        // recorded layout must always be the first batch out.
        final int vboA = defaultQuadVbo();
        final int vaoA = setupPosColorVao(vboA);

        final int vboB = newBuffer();
        final ByteBuffer bData = MemoryUtilities.memAlloc(4 * 8).order(ByteOrder.nativeOrder());
        for (int i = 0; i < 4; i++) {
            bData.putFloat(DEFAULT_QUAD_POSITIONS[i * 2]);
            bData.putFloat(DEFAULT_QUAD_POSITIONS[i * 2 + 1]);
        }
        bData.flip();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboB);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, bData, GL15.GL_STATIC_DRAW);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        MemoryUtilities.memFree(bData);

        final int vaoB = newVao();
        GLStateManager.glBindVertexArray(vaoB);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboB);
        GLStateManager.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 8, 0);
        GLStateManager.glEnableVertexAttribArray(0);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GLStateManager.glBindVertexArray(0);

        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2);

        // Record (A, B) and (B, A) as independent lists; each list's batch order
        // must match its own recording order.
        final int listAB = newList();
        GLStateManager.glNewList(listAB, GL11.GL_COMPILE);
        GLStateManager.glBindVertexArray(vaoA);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glBindVertexArray(vaoB);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glEndList();

        final int listBA = newList();
        GLStateManager.glNewList(listBA, GL11.GL_COMPILE);
        GLStateManager.glBindVertexArray(vaoB);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glBindVertexArray(vaoA);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glEndList();

        final List<BatchedIndexedDrawCmd> ab = allBatchedCmds(listAB);
        final List<BatchedIndexedDrawCmd> ba = allBatchedCmds(listBA);
        // Placeholders sit in command-stream order (A-first vs B-first).
        // Batches must be ordered so that placeholder[0] points at batch[0].
        assertEquals(ab.get(0).getSharedVAO(), batchesOf(listAB).get(0).getSharedVAO(),
            "listAB: batch[0] must match the first-recorded layout");
        assertEquals(ab.get(1).getSharedVAO(), batchesOf(listAB).get(1).getSharedVAO(),
            "listAB: batch[1] must match the second-recorded layout");
        assertEquals(ba.get(0).getSharedVAO(), batchesOf(listBA).get(0).getSharedVAO(),
            "listBA: batch[0] must match the first-recorded layout");
        assertEquals(ba.get(1).getSharedVAO(), batchesOf(listBA).get(1).getSharedVAO(),
            "listBA: batch[1] must match the second-recorded layout");
    }

    // === local helpers ===

    private static void assertUintIndices(ByteBuffer ib, int... expected) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], ib.getInt(i * 4), "index[" + i + "]");
        }
    }
}
