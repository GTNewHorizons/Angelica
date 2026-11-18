package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizons.angelica.glsm.DisplayListManager.RecordMode;
import com.gtnewhorizons.angelica.glsm.backend.GLDebugMessageListener;
import com.gtnewhorizons.angelica.glsm.ffp.FfpExtendedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.glsm.recording.CommandRecorder;
import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.ImmediateModeRecorder;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawCapture;
import com.gtnewhorizons.angelica.glsm.streaming.LineQuads;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.KHRDebugCallback;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.LOGGER;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.acquireDrawLock;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.ctx;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.guardUnsupportedFFP;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.initConfig;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.rejectExitCapture;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.releaseDrawLock;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.warnOnce;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class GLSMDraws {

    private GLSMDraws() {}

    public static long drawCalls;

    /**
     * Check if a display list exists (has been compiled and stored). Delegates to DisplayListManager.
     *
     * @param list The display list ID to check
     * @return true if the display list exists, false otherwise
     */
    public static boolean displayListExists(int list) {
        return DisplayListManager.displayListExists(list);
    }

    private static final String PIXELTRANSFER_MSG = "glPixelTransfer is not available in GL 3.3 core profile.";

    public static void glPixelTransferf(int pname, float param) {
        guardUnsupportedFFP("glPixelTransfer", PIXELTRANSFER_MSG);
    }

    public static void glPixelTransferi(int pname, int param) {
        guardUnsupportedFFP("glPixelTransfer", PIXELTRANSFER_MSG);
    }

    private static final String RASTERPOS_MSG = "glRasterPos is not available in GL 3.3 core profile.";

    public static void glRasterPos2f(float x, float y) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }
    public static void glRasterPos2d(double x, double y) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }
    public static void glRasterPos2i(int x, int y) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }
    public static void glRasterPos3f(float x, float y, float z) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }
    public static void glRasterPos3d(double x, double y, double z) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }
    public static void glRasterPos3i(int x, int y, int z) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }
    public static void glRasterPos4f(float x, float y, float z, float w) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }
    public static void glRasterPos4d(double x, double y, double z, double w) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }
    public static void glRasterPos4i(int x, int y, int z, int w) { guardUnsupportedFFP("glRasterPos", RASTERPOS_MSG); }

    public static void glBegin(int mode) {
        rejectExitCapture();
        ctx().unit23TexCoordSetDuringDraw = false;
        if (DisplayListManager.isRecording()) {
            ImmediateModeRecorder.begin(mode);
            return;
        }
        ImmediateModeRecorder.beginLive(mode);
    }

    public static void glEnd() {
        rejectExitCapture();
        final DirectTessellator result = ImmediateModeRecorder.end();
        if (result != null) {
            if (DisplayListManager.isRecording()) {
                if (DisplayListManager.isCompileAndExecute() && initConfig != null && initConfig.getDirectDrawer() != null) {
                    final CommandRecorder recorder = DisplayListManager.pauseRecording();
                    initConfig.getDirectDrawer().accept(result);
                    DisplayListManager.resumeRecording(recorder);
                }
                DisplayListManager.addImmediateModeDraw(result);
                return;
            }
            if (initConfig != null && initConfig.getDirectDrawer() != null) {
                initConfig.getDirectDrawer().accept(result);
            }
        }
    }

    public static void glVertex2f(float x, float y) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.vertex(x, y, 0.0f);
        }
    }

    public static void glVertex2i(int x, int y) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.vertex((float) x, (float) y, 0.0f);
        }
    }

    public static void glVertex2d(double x, double y) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.vertex((float) x, (float) y, 0.0f);
        }
    }

    public static void glVertex3f(float x, float y, float z) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.vertex(x, y, z);
        }
    }

    public static void glVertex3d(double x, double y, double z) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.vertex((float) x, (float) y, (float) z);
        }
    }

    public static void glVertex3i(int x, int y, int z) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.vertex((float) x, (float) y, (float) z);
        }
    }

    public static void preDraw(GLContextState glCtx, int drawMode) {
        prepareLineEmulation(glCtx, drawMode);
        glCtx.ffp.preDraw(glCtx);
        GLSMVertexInput.prepareClientArrays(glCtx);
    }

    public static void glDrawElements(int mode, ByteBuffer indices) {
        rejectExitCapture();
        drawCalls++;
        CommandRecorder savedRecorder = null;
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            final IndexedDrawCapture capture = IndexedDrawCapture.createFromClientIndices(mode, indices.remaining(), GL11.GL_UNSIGNED_BYTE, MemoryUtilities.memAddress(indices), indices.remaining());
            if (capture != null) DisplayListManager.recordIndexedDrawCapture(capture);
            if (recordMode == RecordMode.COMPILE) return;
            savedRecorder = DisplayListManager.pauseRecording();
        } else if (FeedbackManager.isFeedbackMode()) {
            FeedbackManager.processDrawElements(mode, indices);
            return;
        }
        final GLContextState glCtx = ctx();
        final boolean locked = acquireDrawLock();
        try {
            if (mode == GL11.GL_QUADS) {
                QuadConverter.drawQuadElementsAsTriangles(glCtx, indices.remaining(), GL11.GL_UNSIGNED_BYTE, indices);
                return;
            }
            if (widensLineDraw(mode, indices.remaining())) {
                drawLinesAsQuads(glCtx, ImmediateModeRecorder.processDrawElementsFromAttribs(mode, indices.remaining(), GL11.GL_UNSIGNED_BYTE, MemoryUtilities.memAddress(indices)));
                return;
            }
            preDraw(glCtx, mode);
            RENDER_BACKEND.drawElements(mode, indices);
        } finally {
            if (locked) releaseDrawLock();
            if (savedRecorder != null) DisplayListManager.resumeRecording(savedRecorder);
        }
    }

    public static void glDrawElements(int mode, IntBuffer indices) {
        rejectExitCapture();
        drawCalls++;
        CommandRecorder savedRecorder = null;
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            final IndexedDrawCapture capture = IndexedDrawCapture.createFromClientIndices(mode, indices.remaining(), GL11.GL_UNSIGNED_INT, MemoryUtilities.memAddress(indices), (long) indices.remaining() << 2);
            if (capture != null) DisplayListManager.recordIndexedDrawCapture(capture);
            if (recordMode == RecordMode.COMPILE) return;
            savedRecorder = DisplayListManager.pauseRecording();
        } else if (FeedbackManager.isFeedbackMode()) {
            FeedbackManager.processDrawElements(mode, indices);
            return;
        }
        final GLContextState glCtx = ctx();
        final boolean locked = acquireDrawLock();
        try {
            if (mode == GL11.GL_QUADS) {
                QuadConverter.drawQuadElementsAsTriangles(glCtx, indices);
                return;
            }
            if (widensLineDraw(mode, indices.remaining())) {
                drawLinesAsQuads(glCtx, ImmediateModeRecorder.processDrawElementsFromAttribs(mode, indices.remaining(), GL11.GL_UNSIGNED_INT, MemoryUtilities.memAddress(indices)));
                return;
            }
            preDraw(glCtx, mode);
            RENDER_BACKEND.drawElements(mode, indices);
        } finally {
            if (locked) releaseDrawLock();
            if (savedRecorder != null) DisplayListManager.resumeRecording(savedRecorder);
        }
    }

    public static void glDrawElements(int mode, ShortBuffer indices) {
        rejectExitCapture();
        drawCalls++;
        CommandRecorder savedRecorder = null;
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            final IndexedDrawCapture capture = IndexedDrawCapture.createFromClientIndices(mode, indices.remaining(), GL11.GL_UNSIGNED_SHORT, MemoryUtilities.memAddress(indices), (long) indices.remaining() << 1);
            if (capture != null) DisplayListManager.recordIndexedDrawCapture(capture);
            if (recordMode == RecordMode.COMPILE) return;
            savedRecorder = DisplayListManager.pauseRecording();
        } else if (FeedbackManager.isFeedbackMode()) {
            FeedbackManager.processDrawElements(mode, indices);
            return;
        }
        final GLContextState glCtx = ctx();
        final boolean locked = acquireDrawLock();
        try {
            if (mode == GL11.GL_QUADS) {
                QuadConverter.drawQuadElementsAsTriangles(glCtx, indices);
                return;
            }
            if (widensLineDraw(mode, indices.remaining())) {
                drawLinesAsQuads(glCtx, ImmediateModeRecorder.processDrawElementsFromAttribs(mode, indices.remaining(), GL11.GL_UNSIGNED_SHORT, MemoryUtilities.memAddress(indices)));
                return;
            }
            preDraw(glCtx, mode);
            RENDER_BACKEND.drawElements(mode, indices);
        } finally {
            if (locked) releaseDrawLock();
            if (savedRecorder != null) DisplayListManager.resumeRecording(savedRecorder);
        }
    }

    public static void glDrawElements(int mode, int count, int type, ByteBuffer indices) {
        rejectExitCapture();
        drawCalls++;
        CommandRecorder savedRecorder = null;
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            final IndexedDrawCapture capture = IndexedDrawCapture.createFromClientIndices(mode, count, type, MemoryUtilities.memAddress(indices), indices.remaining());
            if (capture != null) DisplayListManager.recordIndexedDrawCapture(capture);
            if (recordMode == RecordMode.COMPILE) return;
            savedRecorder = DisplayListManager.pauseRecording();
        } else if (FeedbackManager.isFeedbackMode()) {
            FeedbackManager.processDrawElements(mode, count, type, indices);
            return;
        }
        final GLContextState glCtx = ctx();
        final boolean locked = acquireDrawLock();
        try {
            if (mode == GL11.GL_QUADS) {
                QuadConverter.drawQuadElementsAsTriangles(glCtx, count, type, indices);
                return;
            }
            if (widensLineDraw(mode, count)) {
                drawLinesAsQuads(glCtx, ImmediateModeRecorder.processDrawElementsFromAttribs(mode, count, type, MemoryUtilities.memAddress(indices)));
                return;
            }
            preDraw(glCtx, mode);
            RENDER_BACKEND.drawElements(mode, count, type, indices);
        } finally {
            if (locked) releaseDrawLock();
            if (savedRecorder != null) DisplayListManager.resumeRecording(savedRecorder);
        }
    }

    public static void glDrawElements(int mode, int indices_count, int type, long indices_buffer_offset) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        drawCalls++;
        CommandRecorder savedRecorder = null;
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            // Core profile: a default VAO is generated at init and glBindVertexArray(0)
            // is redirected to it, so a VAO is always bound here -- no fallback branches.
            final IndexedDrawCapture capture = IndexedDrawCapture.create(mode, indices_count, type, indices_buffer_offset, glCtx.vaos.boundEBO);
            if (capture != null) {
                DisplayListManager.recordIndexedDrawCapture(capture);
            }
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
            savedRecorder = DisplayListManager.pauseRecording();
        } else if (FeedbackManager.isFeedbackMode()) {
            FeedbackManager.processDrawElements(mode, indices_count, type, indices_buffer_offset);
            return;
        }
        final boolean locked = acquireDrawLock();
        try {
            if (widensLineDraw(mode, indices_count)) {
                drawLinesAsQuads(glCtx, lineElementsFromBuffer(glCtx, mode, indices_count, type, indices_buffer_offset));
                return;
            }
            final boolean ffpExt = FfpExtendedAttribs.maybeBindIndexed(glCtx, mode, indices_count, type, indices_buffer_offset);
            try {
                if (mode == GL11.GL_QUADS) {
                    QuadConverter.drawQuadElementsAsTriangles(glCtx, indices_count, type, indices_buffer_offset);
                    return;
                }
                preDraw(glCtx, mode);
                RENDER_BACKEND.drawElements(mode, indices_count, type, indices_buffer_offset);
            } finally {
                if (ffpExt) FfpExtendedAttribs.unbind();
            }
        } finally {
            if (locked) releaseDrawLock();
            if (savedRecorder != null) DisplayListManager.resumeRecording(savedRecorder);
        }
    }

    public static void glMultiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        rejectExitCapture();
        drawCalls += drawcount;
        RENDER_BACKEND.multiDrawElementsIndirect(mode, type, indirect, drawcount, stride);
    }

    public static void glMultiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex) {
        rejectExitCapture();
        drawCalls += drawcount;
        RENDER_BACKEND.multiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawcount, pBaseVertex);
    }

    public static void glDrawElementsBaseVertex(int mode, int count, int type, long indices, int basevertex) {
        rejectExitCapture();
        drawCalls++;
        RENDER_BACKEND.drawElementsBaseVertex(mode, count, type, indices, basevertex);
    }

    public static void glDrawElementsInstanced(int mode, int count, int type, long indices, int primcount) {
        rejectExitCapture();
        drawCalls++;
        final GLContextState glCtx = ctx();
        final boolean locked = acquireDrawLock();
        try {
            if (mode == GL11.GL_QUADS) {
                QuadConverter.drawQuadElementsAsTrianglesInstanced(glCtx, count, type, indices, primcount);
                return;
            }
            warnIfInstancedWideLines(mode);
            preDraw(glCtx, mode);
            RENDER_BACKEND.drawElementsInstanced(mode, count, type, indices, primcount);
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    public static void glDrawArraysInstanced(int mode, int first, int count, int primcount) {
        rejectExitCapture();
        drawCalls++;
        final GLContextState glCtx = ctx();
        final boolean locked = acquireDrawLock();
        try {
            if (mode == GL11.GL_QUADS) {
                QuadConverter.drawQuadsAsTrianglesInstanced(glCtx, first, count, primcount);
            } else if (mode == GL11.GL_QUAD_STRIP) {
                preDraw(glCtx, GL11.GL_TRIANGLES);
                RENDER_BACKEND.drawArraysInstanced(GL11.GL_TRIANGLE_STRIP, first, count & ~1, primcount);
            } else if (mode == GL11.GL_POLYGON) {
                preDraw(glCtx, GL11.GL_TRIANGLES);
                RENDER_BACKEND.drawArraysInstanced(GL11.GL_TRIANGLE_FAN, first, count, primcount);
            } else {
                warnIfInstancedWideLines(mode);
                preDraw(glCtx, mode);
                RENDER_BACKEND.drawArraysInstanced(mode, first, count, primcount);
            }
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    public static void glDrawRangeElements(int mode, int start, int end, int count, int type, long indices) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final boolean locked = acquireDrawLock();
        try {
            if (widensLineDraw(mode, count)) {
                drawLinesAsQuads(glCtx, lineElementsFromBuffer(glCtx, mode, count, type, indices));
                return;
            }
            preDraw(glCtx, mode);
            RENDER_BACKEND.drawRangeElements(mode, start, end, count, type, indices);
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    public static void glDrawRangeElementsBaseVertex(int mode, int start, int end, int count, int type, long indices, int baseVertex) {
        rejectExitCapture();
        RENDER_BACKEND.drawRangeElementsBaseVertex(mode, start, end, count, type, indices, baseVertex);
    }

    public static void glMultiDrawArrays(int mode, IntBuffer firsts, IntBuffer counts) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final boolean locked = acquireDrawLock();
        try {
            if (LineQuads.isLineMode(mode) && ffpWidensLineQuads()) {
                for (int i = firsts.position(); i < firsts.limit(); i++) {
                    final int count = counts.get(counts.position() + i - firsts.position());
                    if (LineQuads.segmentCount(mode, count) == 0) continue;
                    drawLinesAsQuads(glCtx, ImmediateModeRecorder.processDrawArraysFromAttribs(mode, firsts.get(i), count));
                }
                return;
            }
            preDraw(glCtx, mode);
            RENDER_BACKEND.multiDrawArrays(mode, firsts, counts);
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    public static void glPrimitiveRestartIndex(int index) {
        rejectExitCapture();
        RENDER_BACKEND.primitiveRestartIndex(index);
    }

    public static void glDrawArrays(int mode, int first, int count) {
        rejectExitCapture();
        drawCalls++;
        CommandRecorder savedRecorder = null;
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            final DirectTessellator result = ImmediateModeRecorder.processDrawArraysFromAttribs(mode, first, count);
            if (result != null) {
                DisplayListManager.addImmediateModeDraw(result);
            }
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
            DisplayListManager.flushAll();
            savedRecorder = DisplayListManager.pauseRecording();
        } else if (FeedbackManager.isFeedbackMode()) {
            FeedbackManager.processDrawArrays(mode, first, count);
            return;
        }
        final GLContextState glCtx = ctx();
        final boolean locked = acquireDrawLock();
        try {
            if (widensLineDraw(mode, count)) {
                drawLinesAsQuads(glCtx, ImmediateModeRecorder.processDrawArraysFromAttribs(mode, first, count));
                return;
            }
            final boolean ffpExt = FfpExtendedAttribs.maybeBind(glCtx, mode, first, count);
            try {
                if (mode == GL11.GL_QUADS) {
                    QuadConverter.drawQuadsAsTriangles(glCtx, first, count);
                } else if (mode == GL11.GL_QUAD_STRIP) {
                    preDraw(glCtx, GL11.GL_TRIANGLES);
                    RENDER_BACKEND.drawArrays(GL11.GL_TRIANGLE_STRIP, first, count & ~1);
                } else if (mode == GL11.GL_POLYGON) {
                    preDraw(glCtx, GL11.GL_TRIANGLES);
                    RENDER_BACKEND.drawArrays(GL11.GL_TRIANGLE_FAN, first, count);
                } else {
                    preDraw(glCtx, mode);
                    RENDER_BACKEND.drawArrays(mode, first, count);
                }
            } finally {
                if (ffpExt) FfpExtendedAttribs.unbind();
            }
        } finally {
            if (locked) releaseDrawLock();
            if (savedRecorder != null) DisplayListManager.resumeRecording(savedRecorder);
        }
    }

    public static void glNewList(int list, int mode) {
        rejectExitCapture();
        DisplayListManager.glNewList(list, mode);
    }

    public static void glEndList() {
        rejectExitCapture();
        DisplayListManager.glEndList();
    }

    static final DisplayListIDAllocator displayListIdAllocator = new DisplayListIDAllocator();

    public static int glGenLists(int range) {
        rejectExitCapture();
        return displayListIdAllocator.allocRange(range);
    }

    public static boolean glIsList(int list) {
        rejectExitCapture();
        return displayListIdAllocator.isAllocated(list);
    }

    /**
     * Delete display lists and free their VBO resources.
     */
    public static void glDeleteLists(int list, int range) {
        rejectExitCapture();
        final boolean locked = acquireDrawLock();
        try {
            synchronized (displayListIdAllocator) {
                DisplayListManager.glDeleteLists(list, range);
                displayListIdAllocator.freeRange(list, range);
            }
        } finally {
            if (locked) releaseDrawLock();
        }
    }

    /**
     * Get a compiled display list from the cache. Used for executing nested display lists.
     *
     * @param list The display list ID
     * @return The CompiledDisplayList, or null if not found
     */
    public static CompiledDisplayList getDisplayList(int list) {
        return DisplayListManager.getDisplayList(list);
    }

    public static void glCallList(int list) {
        rejectExitCapture();
        if (!GLDebug.isActive()) {
            DisplayListManager.glCallList(list);
            return;
        }
        GLDebug.pushGroup("glCallList ", list);
        try {
            DisplayListManager.glCallList(list);
        } finally {
            GLDebug.popGroup();
        }
    }

    public static int getListMode() {
        return DisplayListManager.getListMode();
    }

    public static boolean ffpWidensLineQuads() {
        final GLContextState glCtx = ctx();
        return GLSMRasterizer.wideLineEmulationEnabled && glCtx.activeProgram == 0 && glCtx.lineState.getWidth() > 1.0f
            && !DisplayListManager.isRecording();
    }

    private static boolean widensLineDraw(int mode, int count) {
        return LineQuads.isLineMode(mode) && LineQuads.segmentCount(mode, count) > 0 && ffpWidensLineQuads();
    }

    private static void drawLinesAsQuads(GLContextState glCtx, DirectTessellator lines) {
        if (lines == null) return;
        if (readsBufferObject(glCtx.vaos)) {
            warnOnce("line-readback", "Wide GL_LINES drawn from a buffer object; widening them reads the buffer back, which waits for the GPU");
        }
        final int vao = glCtx.boundVAO;
        final int vbo = glCtx.boundVBO;
        TessellatorStreamingDrawer.drawDirect(lines);
        GLSMVertexInput.glBindVertexArray(vao);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
    }

    private static DirectTessellator lineElementsFromBuffer(GLContextState glCtx, int mode, int count, int type, long offset) {
        if (glCtx.vaos.boundEBO == 0) return null;
        final ByteBuffer indices = MemoryUtilities.memAlloc(count * GLTypes.sizeBytes(type));
        try {
            GLSMBuffers.glGetBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, offset, indices);
            return ImmediateModeRecorder.processDrawElementsFromAttribs(mode, count, type, MemoryUtilities.memAddress0(indices));
        } finally {
            MemoryUtilities.memFree(indices);
        }
    }

    private static void warnIfInstancedWideLines(int mode) {
        if (LineQuads.isLineMode(mode) && ffpWidensLineQuads()) {
            warnOnce("instanced-wide-lines", "Instanced wide GL_LINES are not widened and draw 1 px wide");
        }
    }

    private static boolean readsBufferObject(VAOManager vaos) {
        for (int i = 0; i < VAOManager.MAX_ATTRIBS; i++) {
            final VAOManager.Attrib attrib = vaos.attrib(i);
            if (attrib != null && attrib.enabled && attrib.vboId != 0) return true;
        }
        return false;
    }

    private static void prepareLineEmulation(GLContextState glCtx, int drawMode) {
        setLineStippleActive(glCtx, (LineQuads.isLineMode(drawMode) || glCtx.lineQuadsActive) && glCtx.lineStippleState.isEnabled());
    }

    private static void setLineStippleActive(GLContextState glCtx, boolean active) {
        if (glCtx.lineStippleActive == active) return;
        glCtx.lineStippleActive = active;
        RENDER_BACKEND.provokingVertex(active ? GL32.GL_FIRST_VERTEX_CONVENTION : GL32.GL_LAST_VERTEX_CONVENTION);
    }

    public static void glFlush() {
        rejectExitCapture();
        RENDER_BACKEND.flush();
    }

    public static void glFinish() {
        rejectExitCapture();
        RENDER_BACKEND.finish();
    }

    public static void glMultiDrawArraysIndirectCount(int mode, long indirect, long drawcount, int maxdrawcount, int stride) {
        rejectExitCapture();
        RENDER_BACKEND.multiDrawArraysIndirectCount(mode, indirect, drawcount, maxdrawcount, stride);
    }

    public static void glMultiDrawElementsIndirectCount(int mode, int type, long indirect, long drawcount, int maxdrawcount, int stride) {
        rejectExitCapture();
        RENDER_BACKEND.multiDrawElementsIndirectCount(mode, type, indirect, drawcount, maxdrawcount, stride);
    }

    public static void glCallLists(IntBuffer lists) {
        while (lists.hasRemaining()) {
            final int listId = lists.get() + ctx().listBase;
            glCallList(listId);
        }
    }

    public static void glCallLists(ShortBuffer lists) {
        while (lists.hasRemaining()) {
            final int listId = (lists.get() & 0xFFFF) + ctx().listBase;
            glCallList(listId);
        }
    }

    public static void glCallLists(ByteBuffer lists) {
        while (lists.hasRemaining()) {
            final int listId = (lists.get() & 0xFF) + ctx().listBase;
            glCallList(listId);
        }
    }

    public static void glListBase(int base) {
        rejectExitCapture();
        ctx().listBase = base;
    }
    public static void nglMultiDrawElementsBaseVertex(int mode, long count, int type, long indices, int primcount, long basevertex) {
        rejectExitCapture();
        RENDER_BACKEND.multiDrawElementsBaseVertex(mode, count, type, indices, primcount, basevertex);
    }

    public static void glObjectLabel(int identifier, int name, CharSequence label) { RENDER_BACKEND.objectLabel(identifier, name, label); }
    public static String glGetObjectLabel(int identifier, int name, int bufSize) { rejectExitCapture(); return RENDER_BACKEND.getObjectLabel(identifier, name); }
    public static void glPushDebugGroup(int source, int id, CharSequence message) { RENDER_BACKEND.pushDebugGroup(source, id, message); }
    public static void glPopDebugGroup() { RENDER_BACKEND.popDebugGroup(); }
    public static void glDebugMessageInsert(int source, int type, int id, int severity, CharSequence buf) { RENDER_BACKEND.debugMessageInsert(source, type, id, severity, buf); }
    public static void glDebugMessageControl(int source, int type, int severity, IntBuffer ids, boolean enabled) { RENDER_BACKEND.debugMessageControl(source, type, severity, ids, enabled); }

    public static int glGetDebugMessageLog(int count, IntBuffer sources, IntBuffer types, IntBuffer ids, IntBuffer severities, IntBuffer lengths, ByteBuffer messageLog) {
        rejectExitCapture();
        return RENDER_BACKEND.getDebugMessageLog(count, sources, types, ids, severities, lengths, messageLog);
    }

    public static void registerDebugMessageListener(GLDebugMessageListener listener, long userParam) {
        RENDER_BACKEND.debugMessageCallback(listener, userParam);
    }

    public static void glDebugMessageCallback(KHRDebugCallback callback) {
        RENDER_BACKEND.debugMessageCallback(GLDebug.adaptDebugCallback(callback), 0L);
    }

    public static void glAccum(int op, float value) {
        guardUnsupportedFFP("glAccum", "The accumulation buffer is not available in GL 3.3 core profile.");
    }

    public static void glFeedbackBuffer(int type, FloatBuffer buffer) {
        rejectExitCapture();
        FeedbackManager.glFeedbackBuffer(type, buffer);
    }

    public static int glRenderMode(int mode) {
        rejectExitCapture();
        return FeedbackManager.glRenderMode(mode);
    }

    public static void glPassThrough(float token) {
        rejectExitCapture();
        FeedbackManager.glPassThrough(token);
    }

    private static boolean selectWarned = false;

    public static void glSelectBuffer(IntBuffer buffer) {
        rejectExitCapture();
        if (!selectWarned) {
            LOGGER.warn("glSelectBuffer: selection mode not emulated");
            selectWarned = true;
        }
    }

    public static void glInitNames() { /* no-op: ignored outside GL_SELECT */ }

    public static void glPushName(int name) { /* no-op */ }

    public static void glPopName() { /* no-op */ }

    public static void glLoadName(int name) { /* no-op */ }

    /**
     * Draw modes that rely on the previous vertex data cannot be merged currently.
     * It is possible to merge them using Index Buffers, but currently unimplemented.
     */
    public static boolean isContinuousDraw(int drawMode) {
        return drawMode == GL11.GL_TRIANGLE_STRIP || drawMode == GL11.GL_TRIANGLE_FAN
            || drawMode == GL11.GL_LINE_STRIP || drawMode == GL11.GL_LINE_LOOP
            || drawMode == GL11.GL_QUAD_STRIP || drawMode == GL11.GL_POLYGON;
    }
}
