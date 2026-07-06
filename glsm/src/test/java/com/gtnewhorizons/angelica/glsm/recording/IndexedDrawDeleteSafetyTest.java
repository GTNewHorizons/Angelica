package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawBatch;
import com.gtnewhorizons.angelica.glsm.recording.support.DisplayListTestFixture;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndexedDrawDeleteSafetyTest extends DisplayListTestFixture {

    @Test
    void vaoEboMapSweep_clearsStaleEntryOnForeignVao() {
        final int vbo = defaultQuadVbo();
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 3);
        final int vao = setupPosColorVao(vbo);
        final int list = recordSingleIndexedDraw(vao, ebo, GL11.GL_QUADS, 4, GL11.GL_UNSIGNED_SHORT);

        final IndexedDrawBatch batch = firstBatch(list);
        final int sharedEbo = batch.getSharedEBO();

        final int foreignVao = newVao();
        GLStateManager.glBindVertexArray(foreignVao);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, sharedEbo);
        GLStateManager.glBindVertexArray(0);

        GLStateManager.glDeleteLists(list, 1);
        untrackList(list);

        GLStateManager.glBindVertexArray(foreignVao);
        assertEquals(0, GLStateManager.getBoundEBO(), "vaoEboMap still references the freed sharedEBO — non-gen crash imminent");
        GLStateManager.glBindVertexArray(0);
    }

    @Test
    void bindBufferCycle_afterListDelete_isGlNoError() {
        final int vbo = defaultQuadVbo();
        final int ebo = uploadEbo(GL11.GL_UNSIGNED_SHORT, 0, 1, 2, 3);
        final int vao = setupPosColorVao(vbo);
        final int list = recordSingleIndexedDraw(vao, ebo, GL11.GL_QUADS, 4, GL11.GL_UNSIGNED_SHORT);

        final int sharedEbo = firstBatch(list).getSharedEBO();
        final int foreignVao = newVao();
        GLStateManager.glBindVertexArray(foreignVao);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, sharedEbo);
        GLStateManager.glBindVertexArray(0);

        GLStateManager.glDeleteLists(list, 1);
        untrackList(list);

        GLStateManager.glBindVertexArray(foreignVao);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
        final int prevEbo = GLStateManager.getBoundEBO();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevEbo);
        final int err = GL11.glGetError();
        assertEquals(GL11.GL_NO_ERROR, err, () -> "bindBuffer(prevEbo=" + prevEbo + ") hit GL 0x" + Integer.toHexString(err) + " — cache carried a freed id through a VAO rebind");
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
        final int prog = linkMinimalProgram();
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}

        GLStateManager.glUseProgram(prog);
        assertEquals(prog, GLStateManager.getActiveProgram());

        GLStateManager.glDeleteProgram(0);

        assertEquals(prog, GLStateManager.getActiveProgram(), "glDeleteProgram(0) must be a silent no-op");

        GLStateManager.glUseProgram(0);
        GLStateManager.glDeleteProgram(prog);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
    }

    @Test
    void activeProgram_clearedOnDelete() {
        final int prog = linkMinimalProgram();
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}

        GLStateManager.glUseProgram(prog);
        assertEquals(prog, GLStateManager.getActiveProgram());

        GLStateManager.glDeleteProgram(prog);

        assertEquals(0, GLStateManager.getActiveProgram(), "activeProgram not cleared when the active program was deleted");

        GLStateManager.glUseProgram(0);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
    }

    private static int linkMinimalProgram() {
        final int vert = GLStateManager.glCreateShader(GL20.GL_VERTEX_SHADER);
        GLStateManager.glShaderSource(vert, MIN_VERT_SRC);
        GLStateManager.glCompileShader(vert);
        if (GL20.glGetShaderi(vert, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new AssertionError("vertex shader failed to compile: " + GL20.glGetShaderInfoLog(vert, 4096));
        }

        final int frag = GLStateManager.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GLStateManager.glShaderSource(frag, MIN_FRAG_SRC);
        GLStateManager.glCompileShader(frag);
        if (GL20.glGetShaderi(frag, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new AssertionError("fragment shader failed to compile: " + GL20.glGetShaderInfoLog(frag, 4096));
        }

        final int prog = GLStateManager.glCreateProgram();
        GLStateManager.glAttachShader(prog, vert);
        GLStateManager.glAttachShader(prog, frag);
        GLStateManager.glLinkProgram(prog);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new AssertionError("program failed to link: " + GL20.glGetProgramInfoLog(prog, 4096));
        }
        GLStateManager.glDetachShader(prog, vert);
        GLStateManager.glDetachShader(prog, frag);
        GLStateManager.glDeleteShader(vert);
        GLStateManager.glDeleteShader(frag);
        return prog;
    }

    private static final String MIN_VERT_SRC = "#version 330 core\nvoid main(){gl_Position=vec4(0.0);}";
    private static final String MIN_FRAG_SRC = "#version 330 core\nout vec4 oC;void main(){oC=vec4(1.0);}";
}
