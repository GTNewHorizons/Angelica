package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.FfpExtendedAttribs;

/**
 * A class that stores multiple vertex formats & their corresponding buffers.
 */
public final class DisplayListVBO {

    private final SubVBO[] vbos;
    private final int[] extVbos;

    DisplayListVBO(SubVBO[] vbos) {
        this(vbos, null);
    }

    DisplayListVBO(SubVBO[] vbos, int[] extVbos) {
        this.vbos = vbos;
        this.extVbos = extVbos;
    }

    public void delete() {
        for (SubVBO vbo : vbos) {
            vbo.delete();
        }
        if (extVbos != null) {
            for (int extVbo : extVbos) {
                if (extVbo != 0) GLStateManager.glDeleteBuffers(extVbo);
            }
        }
    }

    public SubVBO[] getVBOs() {
        return vbos;
    }

    public SubVBO getVBO(int index) {
        return vbos[index];
    }

    public void render(int index) {
        vbos[index].render();
    }

    public static final class SubVBO {

        private final IVertexArrayObject vao;
        private final int drawMode;
        private final int start;
        private final int count;

        public SubVBO(IVertexArrayObject vao, int drawMode, int start, int count) {
            this.vao = vao;
            this.drawMode = drawMode;
            this.start = start;
            this.count = count;
        }

        public int getStart() {
            return start;
        }

        public int getCount() {
            return count;
        }

        public int getDrawMode() {
            return drawMode;
        }

        public void delete() {
            vao.delete();
        }

        public void render() {
            vao.bind();
            FfpExtendedAttribs.beginInternalDraw();
            try {
                vao.draw(drawMode, start, count);
            } finally {
                FfpExtendedAttribs.endInternalDraw();
            }
            vao.unbind();
        }

        public IVertexArrayObject getVAO() {
            return vao;
        }
    }
}
