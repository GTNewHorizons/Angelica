package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;

/**
 * A class that stores multiple vertex formats & their corresponding buffers.
 */
public final class DisplayListVBO {

    private final SubVBO[] vbos;

    DisplayListVBO(SubVBO[] vbos) {
        this.vbos = vbos;
    }

    public void delete() {
        for (SubVBO vbo : vbos) {
            vbo.delete();
        }
    }

    public SubVBO[] getVBOs() {
        return vbos;
    }

    public void render(int index) {
        vbos[index].render();
    }

    public static final class SubVBO {

        private final IVertexArrayObject vao;
        private final int drawMode;
        private final int start;
        private final int count;
        private final int vertexFlags;

        public SubVBO(IVertexArrayObject vao, int drawMode, int start, int count, int vertexFlags) {
            this.vao = vao;
            this.drawMode = drawMode;
            this.start = start;
            this.count = count;
            this.vertexFlags = vertexFlags;
        }

        public void delete() {
            vao.delete();
        }

        public void render() {
            ShaderManager.getInstance().preDraw(vertexFlags);
            vao.bind();
            vao.draw(drawMode, start, count);
            vao.unbind();
        }

        public IVertexArrayObject getVAO() {
            return vao;
        }
    }
}
