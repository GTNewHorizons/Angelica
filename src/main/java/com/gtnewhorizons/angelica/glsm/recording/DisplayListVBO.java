package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.vbo.IVertexBuffer;

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

    public void render(int index) {
        vbos[index].render();
    }

    static final class SubVBO {

        private final IVertexBuffer vbo;
        private final int drawMode;
        private final int start;
        private final int count;

        public SubVBO(IVertexBuffer vbo, int drawMode, int start, int count) {
            this.vbo = vbo;
            this.drawMode = drawMode;
            this.start = start;
            this.count = count;
        }

        public void delete() {
            vbo.delete();
        }

        public void render() {
            vbo.render(drawMode, start, count);
        }
    }
}
