package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}

        final int query = GL15.glGenQueries();
        GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, query);

        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glEndList();

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

        GLStateManager.glBindVertexArray(batched.getSharedVAO());
        assertEquals(batch.getSharedEBO(), GLStateManager.getBoundEBO(),
            "batched VAO's EBO binding corrupted");
        GLStateManager.glBindVertexArray(0);
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
        GLStateManager.glBindVertexArray(distractorVao);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}

        compiledOf(list).render();

        final int err = GL11.glGetError();
        assertEquals(GL11.GL_NO_ERROR, err,
            () -> "replay produced GL error 0x" + Integer.toHexString(err));

        GLStateManager.glBindVertexArray(0);
    }

    @Test
    void replayThenDelete_thenBindBufferCycle_isGlNoError() {
        final int vbo = defaultQuadVbo();
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 3);
        final int vao = setupPosColorVao(vbo);
        final int list = recordSingleIndexedDraw(vao, ebo, GL11.GL_QUADS, 4, GL11.GL_UNSIGNED_SHORT);

        compiledOf(list).render();


        final int sharedEbo = firstBatch(list).getSharedEBO();
        final int foreignVao = newVao();
        GLStateManager.glBindVertexArray(foreignVao);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, sharedEbo);
        GLStateManager.glBindVertexArray(0);

        GLStateManager.glDeleteLists(list, 1);
        untrackList(list);

        GLStateManager.glBindVertexArray(foreignVao);
        assertEquals(0, GLStateManager.getBoundEBO(), "batched EBO id survived list delete in vaoEboMap — non-gen crash imminent");
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
        final int prevEbo = GLStateManager.getBoundEBO();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevEbo);
        final int err = GL11.glGetError();
        assertEquals(GL11.GL_NO_ERROR, err, () -> "post-delete bindBuffer(" + prevEbo + ") hit GL 0x" + Integer.toHexString(err));
        GLStateManager.glBindVertexArray(0);
    }

    @Test
    void execute_doesNotRestoreVAO_backToBackDraws() {
        final int vbo = defaultQuadVbo();
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 3);
        final int vao = setupPosColorVao(vbo);

        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glDrawElements(GL11.GL_QUADS, 4, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glDrawElements(GL11.GL_QUADS, 4, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glEndList();

        final List<BatchedIndexedDrawCmd> cmds = allBatchedCmds(list);
        assertEquals(2, cmds.size());
        final int sharedVAO = cmds.getFirst().getSharedVAO();
        assertEquals(sharedVAO, cmds.get(1).getSharedVAO());

        final int distractor = newVao();
        GLStateManager.glBindVertexArray(distractor);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}

        compiledOf(list).render();

        assertEquals(sharedVAO, GLStateManager.getBoundVAO());
        GLStateManager.glBindVertexArray(0);
    }

    @Test
    void build_partialFailure_deletesEarlierBatchesAndLeavesPlaceholdersUnfilled() {
        final int vbo = defaultQuadVbo();
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 3);
        final int vao = setupPosColorVao(vbo);
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);

        final IndexedDrawCapture good = IndexedDrawCapture.create(GL11.GL_QUADS, 4, GL11.GL_UNSIGNED_SHORT, 0L, ebo);
        assertNotNull(good);

        // Synthetic capture with a different layout that trips the >2GiB size guard.
        final AttribLayoutKey otherLayout = new AttribLayoutKey(
            new int[]{2}, new int[]{4}, new int[]{GL11.GL_FLOAT}, new boolean[]{false});
        final ByteBuffer tinyVtx = MemoryUtilities.memAlloc(16);
        final ByteBuffer tinyIdx = MemoryUtilities.memAlloc(16);
        final IndexedDrawCapture huge = new IndexedDrawCapture(
            otherLayout, GL11.GL_TRIANGLES,
            Integer.MAX_VALUE / 4, 1, tinyVtx, tinyIdx, 16,
            new BatchedIndexedDrawCmd());

        final IndexedDrawBatchBuilder builder = new IndexedDrawBatchBuilder();
        builder.add(good);
        builder.add(huge);

        try {
            assertThrows(IllegalStateException.class, builder::build);
            assertEquals(0, good.placeholder.getSharedVAO());
            assertEquals(0, huge.placeholder.getSharedVAO());
        } finally {
            MemoryUtilities.memFree(tinyVtx);
            MemoryUtilities.memFree(tinyIdx);
            good.freeBuffers();
            GLStateManager.glBindVertexArray(0);
        }
    }

    @Test
    void recorderFree_releasesUnfilledCaptureNativeBuffers() {
        final CommandRecorder rec = new CommandRecorder();
        final ByteBuffer vtx = MemoryUtilities.memAlloc(64);
        final ByteBuffer idx = MemoryUtilities.memAlloc(32);
        final IndexedDrawCapture cap = new IndexedDrawCapture(
            new AttribLayoutKey(new int[]{0}, new int[]{2}, new int[]{GL11.GL_FLOAT}, new boolean[]{false}),
            GL11.GL_TRIANGLES, 4, 6, vtx, idx, 8, new BatchedIndexedDrawCmd());

        rec.recordIndexedDrawCapture(cap);
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
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glDrawElements(GL11.GL_QUADS, 4, GL11.GL_UNSIGNED_SHORT, 0L);
        GLStateManager.glEndList();

        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}

        compiledOf(list).render();
        compiledOf(list).render();

        final int err = GL11.glGetError();
        assertEquals(GL11.GL_NO_ERROR, err,
            () -> "double replay produced GL error 0x" + Integer.toHexString(err));

        GLStateManager.glBindVertexArray(0);
    }
}
