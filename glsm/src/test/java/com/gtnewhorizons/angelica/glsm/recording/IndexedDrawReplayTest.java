package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.recording.commands.AttribLayoutKey;
import com.gtnewhorizons.angelica.glsm.recording.commands.BatchedIndexedDrawCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawBatch;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawBatchBuilder;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawCapture;
import com.gtnewhorizons.angelica.glsm.recording.support.DisplayListTestFixture;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.util.List;

import static com.gtnewhorizons.angelica.glsm.GlTestSupport.drainErrors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class IndexedDrawReplayTest extends DisplayListTestFixture {
    @Test
    void compileAndExecute_firesLiveAndBakes() {
        // COMPILE_AND_EXECUTE must do two things: (1) run the draw immediately,
        // (2) record a placeholder so the list can replay it later. A samples-passed
        // query wrapping the record block confirms the live half; asserting the
        // placeholder post-endList confirms the baked half.
        final int vbo = defaultQuadVbo();
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 2, 3, 0);
        final int vao = setupPosColorVao(vbo);

        GL11.glViewport(0, 0, 16, 16);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        drainErrors();

        final int query = GL15.glGenQueries();
        GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, query);

        final int list = newList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
        GLSMVertexInput.glBindVertexArray(vao);
        GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLSMDraws.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_SHORT, 0L);
        GLSMVertexInput.glBindVertexArray(0);
        GLSMDraws.glEndList();

        GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);
        final int samples = GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT);
        GL15.glDeleteQueries(query);

        assertTrue(samples > 0, "COMPILE_AND_EXECUTE did not fire the live draw (samples=" + samples + ")");

        final BatchedIndexedDrawCmd baked = firstBatchedCmd(list);
        assertNotNull(baked, "COMPILE_AND_EXECUTE did not bake a placeholder");
        assertEquals(6, baked.getIndexCount());
    }

    @Test
    void sharedVaoRetainsItsEbo() {
        final int vbo = defaultQuadVbo();
        final int callerEbo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 3);
        final int vao = setupPosColorVao(vbo);
        final int list = recordSingleIndexedDraw(vao, callerEbo, GL11.GL_QUADS, 4, GL11.GL_UNSIGNED_SHORT);

        final BatchedIndexedDrawCmd batched = firstBatchedCmd(list);
        assertNotNull(batched);
        final IndexedDrawBatch batch = firstBatch(list);

        replay(list);
        GLSMVertexInput.glBindVertexArray(vaoOf(batch));
        assertEquals(batch.getSharedEBO(), GLSMVertexInput.getBoundEBO(),
            "batched VAO's EBO binding corrupted");
        assertEquals(batch.getSharedEBO(), GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING));
        GLSMVertexInput.glBindVertexArray(0);
    }

    @Test
    void replayAfterSourceMutationAndStateChange() {
        final int vbo = defaultQuadVbo();
        final int callerEbo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 2, 3, 0);
        final int vao = setupPosColorVao(vbo);
        final int list = recordSingleIndexedDraw(vao, callerEbo, GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_SHORT);

        overwriteBufferWithGarbage(GL15.GL_ARRAY_BUFFER, vbo, 4 * POS_COLOR_STRIDE);
        overwriteBufferWithGarbage(GL15.GL_ELEMENT_ARRAY_BUFFER, callerEbo, 6 * 2);

        // Bind unrelated state so the baked command has to restore its own bindings.
        final int distractorVao = newVao();
        GLSMVertexInput.glBindVertexArray(distractorVao);
        GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        drainErrors();

        compiledOf(list).render();

        final int err = GL11.glGetError();
        assertEquals(GL11.GL_NO_ERROR, err,
            () -> "replay produced GL error 0x" + Integer.toHexString(err));

        GLSMVertexInput.glBindVertexArray(0);
    }

    @Test
    void execute_doesNotRestoreVAO_backToBackDraws() {
        final int vbo = defaultQuadVbo();
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 3);
        final int vao = setupPosColorVao(vbo);

        final int list = newList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        GLSMVertexInput.glBindVertexArray(vao);
        GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLSMDraws.glDrawElements(GL11.GL_QUADS, 4, GL11.GL_UNSIGNED_SHORT, 0L);
        GLSMDraws.glDrawElements(GL11.GL_QUADS, 4, GL11.GL_UNSIGNED_SHORT, 0L);
        GLSMDraws.glEndList();

        final List<BatchedIndexedDrawCmd> cmds = allBatchedCmds(list);
        assertEquals(2, cmds.size());

        final int distractor = newVao();
        GLSMVertexInput.glBindVertexArray(distractor);
        drainErrors();

        compiledOf(list).render();

        assertSame(batchOf(cmds.getFirst()), batchOf(cmds.get(1)));
        final int sharedVAO = vaoOf(batchOf(cmds.getFirst()));
        assertNotEquals(0, sharedVAO);
        assertEquals(sharedVAO, GLSMVertexInput.getBoundVAO());
        GLSMVertexInput.glBindVertexArray(0);
    }

    @Test
    void build_partialFailure_deletesEarlierBatchesAndLeavesPlaceholdersUnfilled() {
        final int vbo = defaultQuadVbo();
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 3);
        final int vao = setupPosColorVao(vbo);
        GLSMVertexInput.glBindVertexArray(vao);
        GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);

        final IndexedDrawCapture good = IndexedDrawCapture.create(GL11.GL_QUADS, 4, GL11.GL_UNSIGNED_SHORT, 0L, ebo);
        assertNotNull(good);

        // Synthetic capture with a different layout that trips the >2GiB size guard.
        final AttribLayoutKey otherLayout = new AttribLayoutKey(
            new int[]{2}, new int[]{4}, new int[]{GL11.GL_FLOAT}, new boolean[]{false});
        final ByteBuffer tinyVtx = MemoryUtilities.memAlloc(16);
        final ByteBuffer tinyIdx = MemoryUtilities.memAlloc(16);
        final IndexedDrawCapture huge = new IndexedDrawCapture(
            otherLayout, GL11.GL_TRIANGLES,
            Integer.MAX_VALUE / 4, 1, tinyVtx, tinyIdx,
            new BatchedIndexedDrawCmd());

        final IndexedDrawBatchBuilder builder = new IndexedDrawBatchBuilder();
        builder.add(good);
        builder.add(huge);

        try {
            assertThrows(IllegalStateException.class, builder::build);
            assertNull(batchOf(good.placeholder));
            assertNull(batchOf(huge.placeholder));
        } finally {
            MemoryUtilities.memFree(tinyVtx);
            MemoryUtilities.memFree(tinyIdx);
            good.freeBuffers();
            GLSMVertexInput.glBindVertexArray(0);
        }
    }

    @Test
    void recorderFree_releasesUnfilledCaptureNativeBuffers() {
        final CommandRecorder rec = new CommandRecorder();
        final ByteBuffer vtx = MemoryUtilities.memAlloc(64);
        final ByteBuffer idx = MemoryUtilities.memAlloc(32);
        final IndexedDrawCapture cap = new IndexedDrawCapture(
            new AttribLayoutKey(new int[]{0}, new int[]{2}, new int[]{GL11.GL_FLOAT}, new boolean[]{false}),
            GL11.GL_TRIANGLES, 4, 6, vtx, idx, new BatchedIndexedDrawCmd());

        rec.writeIndexedDrawCapture(cap);
        assertFalse(cap.isFreed());

        rec.free();

        assertTrue(cap.isFreed());
        cap.freeBuffers();
    }

    @Test
    void execute_idempotentReplay_doesNotErrorOnSecondPass() {
        final int vbo = defaultQuadVbo();
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 3);
        final int vao = setupPosColorVao(vbo);

        final int list = newList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        GLSMVertexInput.glBindVertexArray(vao);
        GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLSMDraws.glDrawElements(GL11.GL_QUADS, 4, GL11.GL_UNSIGNED_SHORT, 0L);
        GLSMDraws.glEndList();

        drainErrors();

        compiledOf(list).render();
        compiledOf(list).render();

        final int err = GL11.glGetError();
        assertEquals(GL11.GL_NO_ERROR, err,
            () -> "double replay produced GL error 0x" + Integer.toHexString(err));

        GLSMVertexInput.glBindVertexArray(0);
    }
}
