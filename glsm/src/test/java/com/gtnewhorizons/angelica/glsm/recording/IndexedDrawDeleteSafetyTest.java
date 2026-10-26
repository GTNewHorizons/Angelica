package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
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
        GLSMVertexInput.glBindVertexArray(foreignVao);
        GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, sharedEbo);
        GLSMVertexInput.glBindVertexArray(0);

        GLSMDraws.glDeleteLists(list, 1);
        untrackList(list);

        GLSMVertexInput.glBindVertexArray(foreignVao);
        assertEquals(0, GLSMVertexInput.getBoundEBO(), "vaoEboMap still references the freed sharedEBO - non-gen crash imminent");

        drainErrors();
        final int prevEbo = GLSMVertexInput.getBoundEBO();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevEbo);
        final int err = GL11.glGetError();
        assertEquals(GL11.GL_NO_ERROR, err, () -> "bindBuffer(prevEbo=" + prevEbo + ") hit GL 0x" + Integer.toHexString(err) + " - cache carried a freed id through a VAO rebind");
        GLSMVertexInput.glBindVertexArray(0);
    }

    @Test
    void multipleVaos_sharingOneEbo_allClearedOnEboDelete() {
        // Two VAOs, same EBO. Deleting the EBO must clear vaoEboMap for both,
        // not just whichever VAO happens to be currently bound.
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 3);
        final int vaoA = newVao();
        final int vaoB = newVao();

        GLSMVertexInput.glBindVertexArray(vaoA);
        GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLSMVertexInput.glBindVertexArray(vaoB);
        GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLSMVertexInput.glBindVertexArray(0);

        GLSMBuffers.glDeleteBuffers(ebo);

        GLSMVertexInput.glBindVertexArray(vaoA);
        assertEquals(0, GLSMVertexInput.getBoundEBO(), "vaoA still references freed EBO");
        GLSMVertexInput.glBindVertexArray(vaoB);
        assertEquals(0, GLSMVertexInput.getBoundEBO(), "vaoB still references freed EBO");
        GLSMVertexInput.glBindVertexArray(0);
    }

    @Test
    void glDeleteProgram_zero_isNoOpAndLeavesActiveProgram() {
        final int prog = GlTestSupport.program(GlTestSupport.TRIVIAL_VS, GlTestSupport.TRIVIAL_FS);
        drainErrors();

        GLSMPrograms.glUseProgram(prog);
        assertEquals(prog, GLSMPrograms.getActiveProgram());

        GLSMPrograms.glDeleteProgram(0);

        assertEquals(prog, GLSMPrograms.getActiveProgram(), "glDeleteProgram(0) must be a silent no-op");

        GLSMPrograms.glUseProgram(0);
        GLSMPrograms.glDeleteProgram(prog);
        drainErrors();
    }

    @Test
    void activeProgram_retainedAfterDelete_perGlSpec() {
        final int prog = GlTestSupport.program(GlTestSupport.TRIVIAL_VS, GlTestSupport.TRIVIAL_FS);
        drainErrors();

        GLSMPrograms.glUseProgram(prog);
        assertEquals(prog, GLSMPrograms.getActiveProgram());

        GLSMPrograms.glDeleteProgram(prog);

        assertEquals(prog, GLSMPrograms.getActiveProgram(), "activeProgram must remain set after delete (GL spec: deletion deferred until unbind)");

        GLSMPrograms.glUseProgram(0);
        assertEquals(0, GLSMPrograms.getActiveProgram(), "activeProgram clears once the deleted program is unbound");
        drainErrors();
    }
}
