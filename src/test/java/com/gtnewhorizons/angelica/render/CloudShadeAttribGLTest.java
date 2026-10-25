package com.gtnewhorizons.angelica.render;

import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class CloudShadeAttribGLTest {

    private static final int FACE_SHADE_ATTRIB = 5;
    private static final int VERTICES = 4;

    private final CloudVertexMesh mesh = new CloudVertexMesh();

    @AfterEach
    void tearDown() {
        mesh.deleteVao();
    }

    @Test
    void shadeAttributeStartsOnAFourByteBoundary() {
        final int stride = Reflect.get(mesh, "vertexStride");
        Reflect.set(mesh, "pendingUpload", BufferUtils.createByteBuffer(VERTICES * 2 * stride));
        Reflect.set(mesh, "pendingVertices", VERTICES);
        mesh.beginUpload();

        final IVertexArrayObject vao = Reflect.get(mesh, "vao");
        vao.bind();
        try {
            final VAOManager.Attrib shade = GLStateManager.ctx().vaos.attrib(FACE_SHADE_ATTRIB);
            assertNotNull(shade);
            assertTrue(shade.enabled);
            assertEquals(0L, shade.offset % 4, "AMD Catalyst crashes on an attribute offset that is not a multiple of 4");
            assertEquals(4, shade.size);
        } finally {
            vao.unbind();
        }
    }
}
