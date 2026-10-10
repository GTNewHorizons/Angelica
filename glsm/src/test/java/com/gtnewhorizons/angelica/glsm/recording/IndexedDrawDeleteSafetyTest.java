package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawBatch;
import com.gtnewhorizons.angelica.glsm.recording.support.DisplayListTestFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import static com.gtnewhorizons.angelica.glsm.GlTestSupport.drainErrors;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IndexedDrawDeleteSafetyTest extends DisplayListTestFixture {

    @ParameterizedTest(name = "replayed before delete={0}")
    @ValueSource(booleans = {false, true})
    void listDelete_clearsSharedEboFromForeignVao(boolean replayFirst) {
        final int vbo = defaultQuadVbo();
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 3);
        final int vao = setupPosColorVao(vbo);
        final int list = recordSingleIndexedDraw(vao, ebo, GL11.GL_QUADS, 4, GL11.GL_UNSIGNED_SHORT);

        if (replayFirst) compiledOf(list).render();

        final IndexedDrawBatch batch = firstBatch(list);
        final int sharedEbo = batch.getSharedEBO();

        final int foreignVao = newVao();
        GLStateManager.glBindVertexArray(foreignVao);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, sharedEbo);
        GLStateManager.glBindVertexArray(0);

        GLStateManager.glDeleteLists(list, 1);
        untrackList(list);

        GLStateManager.glBindVertexArray(foreignVao);
        assertEquals(0, GLStateManager.getBoundEBO(), "vaoEboMap still references the freed sharedEBO - non-gen crash imminent");

        drainErrors();
        final int prevEbo = GLStateManager.getBoundEBO();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevEbo);
        final int err = GL11.glGetError();
        assertEquals(GL11.GL_NO_ERROR, err, () -> "bindBuffer(prevEbo=" + prevEbo + ") hit GL 0x" + Integer.toHexString(err) + " - cache carried a freed id through a VAO rebind");
        GLStateManager.glBindVertexArray(0);
    }

    @Test
    void multipleVaos_sharingOneEbo_allClearedOnEboDelete() {
        // Two VAOs, same EBO. Deleting the EBO must clear vaoEboMap for both,
        // not just whichever VAO happens to be currently bound.
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 3);
        final int vaoA = newVao();
        final int vaoB = newVao();

        GLStateManager.glBindVertexArray(vaoA);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glBindVertexArray(vaoB);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glBindVertexArray(0);

        GLStateManager.glDeleteBuffers(ebo);

        GLStateManager.glBindVertexArray(vaoA);
        assertEquals(0, GLStateManager.getBoundEBO(), "vaoA still references freed EBO");
        GLStateManager.glBindVertexArray(vaoB);
        assertEquals(0, GLStateManager.getBoundEBO(), "vaoB still references freed EBO");
        GLStateManager.glBindVertexArray(0);
    }

    @Test
    void glDeleteProgram_zero_isNoOpAndLeavesActiveProgram() {
        final int prog = GlTestSupport.program(GlTestSupport.TRIVIAL_VS, GlTestSupport.TRIVIAL_FS);
        drainErrors();

        GLStateManager.glUseProgram(prog);
        assertEquals(prog, GLStateManager.getActiveProgram());

        GLStateManager.glDeleteProgram(0);

        assertEquals(prog, GLStateManager.getActiveProgram(), "glDeleteProgram(0) must be a silent no-op");

        GLStateManager.glUseProgram(0);
        GLStateManager.glDeleteProgram(prog);
        drainErrors();
    }

    @Test
    void activeProgram_retainedAfterDelete_perGlSpec() {
        final int prog = GlTestSupport.program(GlTestSupport.TRIVIAL_VS, GlTestSupport.TRIVIAL_FS);
        drainErrors();

        GLStateManager.glUseProgram(prog);
        assertEquals(prog, GLStateManager.getActiveProgram());

        GLStateManager.glDeleteProgram(prog);

        assertEquals(prog, GLStateManager.getActiveProgram(), "activeProgram must remain set after delete (GL spec: deletion deferred until unbind)");

        GLStateManager.glUseProgram(0);
        assertEquals(0, GLStateManager.getActiveProgram(), "activeProgram clears once the deleted program is unbound");
        drainErrors();
    }
}
