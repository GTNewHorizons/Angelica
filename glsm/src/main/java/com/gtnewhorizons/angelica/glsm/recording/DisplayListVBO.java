package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizons.angelica.glsm.GLContextState;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import com.gtnewhorizons.angelica.glsm.streaming.LineQuads;
import org.lwjgl.opengl.GL11;
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
        private IVertexArrayObject lineQuads;
        private int lineQuadStart;
        private int lineQuadCount;
        private int lineQuadFlags;

        void setPendingExtVbo(int extVbo) { this.pendingExtVbo = extVbo; }

        void setLineQuads(IVertexArrayObject quads, int start, int count, int flags) {
            this.lineQuads = quads;
            this.lineQuadStart = start;
            this.lineQuadCount = count;
            this.lineQuadFlags = flags;
        }

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
            if (lineQuads != null) lineQuads.delete();
        }

        public void render() {
            if (vao == null) return;
            if (lineQuads != null && GLStateManager.ffpWidensLineQuads()) {
                renderLineQuads();
                return;
            }
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

        private void renderLineQuads() {
            final GLContextState glCtx = GLStateManager.ctx();
            lineQuads.bind();
            VAOManager.setCurrentVertexFlags(lineQuadFlags);
            final boolean culled = LineQuads.disableCulling();
            glCtx.lineQuadsActive = true;
            FfpExtendedAttribs.beginInternalDraw();
            try {
                lineQuads.draw(GL11.GL_TRIANGLES, lineQuadStart, lineQuadCount);
            } finally {
                FfpExtendedAttribs.endInternalDraw();
                glCtx.lineQuadsActive = false;
                LineQuads.restoreCulling(culled);
            }
            lineQuads.unbind();
        }

        public IVertexArrayObject getVAO() {
            return vao;
        }
    }
}
