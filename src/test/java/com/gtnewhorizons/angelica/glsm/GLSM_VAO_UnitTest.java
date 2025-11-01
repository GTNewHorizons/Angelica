package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.opengl.UniversalVAO;
import com.gtnewhorizons.angelica.AngelicaExtension;
import com.gtnewhorizons.angelica.util.GLSMUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyState;

@ExtendWith(AngelicaExtension.class)
public class GLSM_VAO_UnitTest {

    @Test
    void testDefaultStates() {
        verifyState(GL30.GL_VERTEX_ARRAY_BINDING, 0, "GL_VERTEX_ARRAY_BINDING Initial State");
        verifyState(GL13.GL_CLIENT_ACTIVE_TEXTURE, GL13.GL_TEXTURE0, "GL_CLIENT_ACTIVE_TEXTURE Initial State");
        testClientStates();
    }

    @Test
    void testVAOPreservesStates() {

        // Stride = vertexSize * byte size of the type (= 4 for both GL_FLOAT and GL_INT)
        final boolean defaultVertexEnabled = false;
        final int defaultVertexSize = 2;
        final int defaultVertexType = GL11.GL_FLOAT;
        final int defaultVertexStride = defaultVertexSize * 4;

        final boolean vaoVertexEnabled = true;
        final int vaoVertexSize = 4;
        final int vaoVertexType = GL11.GL_INT;
        final int vaoVertexStride = vaoVertexSize * 4;

        GL13.glClientActiveTexture(GL13.GL_TEXTURE1);
        verifyState(GL13.GL_CLIENT_ACTIVE_TEXTURE, GL13.GL_TEXTURE1, "GL_CLIENT_ACTIVE_TEXTURE - Setup");

        // Set the vertex attrib to a defined state
        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        GL11.glVertexPointer(defaultVertexSize, defaultVertexType, defaultVertexStride, 0);

        GLSMUtil.setClientState(GL11.GL_NORMAL_ARRAY, true);
        verifyState(GL11.GL_NORMAL_ARRAY, true, "GL_NORMAL_ARRAY - Setup");

        verifyState(GL11.GL_VERTEX_ARRAY_SIZE, defaultVertexSize, "GL_VERTEX_ARRAY_SIZE - Setup");
        verifyState(GL11.GL_VERTEX_ARRAY_TYPE, defaultVertexType, "GL_VERTEX_ARRAY_TYPE - Setup");
        verifyState(GL11.GL_VERTEX_ARRAY_STRIDE, defaultVertexStride, "GL_VERTEX_ARRAY_STRIDE - Setup");

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        // Verify VAO & VBO creation
        int vao = UniversalVAO.genVertexArrays();
        Assertions.assertTrue(vao > 0, "GL_VERTEX_ARRAY_BINDING - VAO Created");

        UniversalVAO.bindVertexArray(vao);
        verifyState(GL30.GL_VERTEX_ARRAY_BINDING, vao, "GL_VERTEX_ARRAY_BINDING - Bound");

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        verifyState(GL15.GL_ARRAY_BUFFER_BINDING, vbo, "GL_ARRAY_BUFFER_BINDING - Bound");

        verifyState(GL13.GL_CLIENT_ACTIVE_TEXTURE, GL13.GL_TEXTURE1, "GL_CLIENT_ACTIVE_TEXTURE - Preserved");

        // Set & Verify the VAO states

        // VAO's have every client state back to its default state, so GL_NORMAL_ARRAY should be false now
        testClientStates();

        GLSMUtil.setClientState(GL11.GL_VERTEX_ARRAY, vaoVertexEnabled);
        verifyState(GL11.GL_VERTEX_ARRAY, vaoVertexEnabled, "GL_VERTEX_ARRAY - Set");

        GL11.glVertexPointer(vaoVertexSize, vaoVertexType, vaoVertexStride, 0);

        verifyState(GL11.GL_VERTEX_ARRAY_SIZE, vaoVertexSize, "GL_VERTEX_ARRAY_SIZE - Set");
        verifyState(GL11.GL_VERTEX_ARRAY_TYPE, vaoVertexType, "GL_VERTEX_ARRAY_TYPE - Set");
        verifyState(GL11.GL_VERTEX_ARRAY_STRIDE, vaoVertexStride, "GL_VERTEX_ARRAY_STRIDE - Set");

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        // Check if everything got reset to default
        UniversalVAO.bindVertexArray(0);
        verifyState(GL30.GL_VERTEX_ARRAY_BINDING, 0, "GL_VERTEX_ARRAY_BINDING - Unbound");
        verifyState(GL15.GL_ARRAY_BUFFER_BINDING, 0, "GL_ARRAY_BUFFER_BINDING - Unbound");

        verifyState(GL11.GL_VERTEX_ARRAY, defaultVertexEnabled, "GL_VERTEX_ARRAY - Reset");
        verifyState(GL11.GL_VERTEX_ARRAY_SIZE, defaultVertexSize, "GL_VERTEX_ARRAY_SIZE - Reset");
        verifyState(GL11.GL_VERTEX_ARRAY_TYPE, defaultVertexType, "GL_VERTEX_ARRAY_TYPE - Reset");
        verifyState(GL11.GL_VERTEX_ARRAY_STRIDE, defaultVertexStride, "GL_VERTEX_ARRAY_STRIDE - Reset");

        verifyState(GL11.GL_NORMAL_ARRAY, true, "GL_NORMAL_ARRAY - Reset");

        // Check if the VAO states are still valid after re-binding it
        UniversalVAO.bindVertexArray(vao);

        verifyState(GL11.GL_VERTEX_ARRAY, vaoVertexEnabled, "GL_VERTEX_ARRAY - Preserved");
        verifyState(GL11.GL_VERTEX_ARRAY_SIZE, vaoVertexSize, "GL_VERTEX_ARRAY_SIZE - Preserved");
        verifyState(GL11.GL_VERTEX_ARRAY_TYPE, vaoVertexType, "GL_VERTEX_ARRAY_TYPE - Preserved");
        verifyState(GL11.GL_VERTEX_ARRAY_STRIDE, vaoVertexStride, "GL_VERTEX_ARRAY_STRIDE - Preserved");

        UniversalVAO.bindVertexArray(0);


        GL15.glDeleteBuffers(vbo);
        UniversalVAO.deleteVertexArrays(vao);
    }

    private static void testClientStates() {
        verifyState(GL11.GL_VERTEX_ARRAY, false, "GL_VERTEX_ARRAY Default State");
        verifyState(GL11.GL_TEXTURE_COORD_ARRAY, false, "GL_TEXTURE_COORD_ARRAY Default State");
        verifyState(GL11.GL_NORMAL_ARRAY, false, "GL_NORMAL_ARRAY Default State");
        verifyState(GL11.GL_COLOR_ARRAY, false, "GL_COLOR_ARRAY Default State");
        verifyState(GL11.GL_INDEX_ARRAY, false, "GL_INDEX_ARRAY Default State");
    }
}
