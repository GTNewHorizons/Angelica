package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import org.lwjgl.opengl.GL15;
import com.gtnewhorizons.angelica.glsm.ffp.FfpExtendedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;

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
        private final int vertexFlags;
        private int pendingExtVbo = 0;

        void setPendingExtVbo(int extVbo) { this.pendingExtVbo = extVbo; }

        public SubVBO(IVertexArrayObject vao, int drawMode, int start, int count, int vertexFlags) {
            this.vao = vao;
            this.drawMode = drawMode;
            this.start = start;
            this.count = count;
            this.vertexFlags = vertexFlags;
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
            if (vao == null) return;
            vao.bind();
            if (pendingExtVbo != 0) {
                GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, pendingExtVbo);
                ImmediateExtendedAttribHandler.setupExtAttribPointers(0L, ImmediateExtendedAttribHandler.EXT_STRIDE);
                GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                pendingExtVbo = 0;
            }
            if (count <= 0) { vao.unbind(); return; }
            VAOManager.setCurrentVertexFlags(vertexFlags);
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
