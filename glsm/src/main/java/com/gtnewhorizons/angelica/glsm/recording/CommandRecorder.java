package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawBatchBuilder;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawCapture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.*;

public final class CommandRecorder {
    private static final int DEFAULT_CAPACITY = 2048; // Guess at typical display list size

    private ByteBuffer buffer;
    private long basePointer;
    private long writePointer;
    private final ArrayList<DisplayListCommand> complexObjects = new ArrayList<>();
    private final IndexedDrawBatchBuilder indexedDraws = new IndexedDrawBatchBuilder();

    public CommandRecorder() {
        this(DEFAULT_CAPACITY);
    }

    public CommandRecorder(int initialCapacity) {
        this.buffer = memAlloc(initialCapacity);
        this.basePointer = memAddress0(buffer);
        this.writePointer = basePointer;
    }

    // === Low-level write operations ===

    private void writeInt(int value) {
        memPutInt(writePointer, value);
        writePointer += 4;
    }

    private void writeLong(long value) {
        memPutLong(writePointer, value);
        writePointer += 8;
    }

    private void writeFloat(float value) {
        memPutFloat(writePointer, value);
        writePointer += 4;
    }

    private void writeDouble(double value) {
        memPutDouble(writePointer, value);
        writePointer += 8;
    }

    private void ensureCapacity(int needed) {
        final int size = size();
        final int remaining = buffer.capacity() - size;
        if (remaining < needed) {
            final int newCapacity = Math.max(buffer.capacity() * 2, buffer.capacity() + needed);
            buffer = memRealloc(buffer, newCapacity);
            basePointer = memAddress0(buffer);
            writePointer = basePointer + size;
        }
    }

    public IndexedDrawBatchBuilder getIndexedDraws() {
        return indexedDraws;
    }

    public void writeIndexedDrawCapture(IndexedDrawCapture capture) {
        indexedDraws.add(capture);
        writeComplexCommand(capture.placeholder);
    }

    public void free() {
        for (IndexedDrawCapture c : indexedDraws.getCaptures()) {
            c.freeBuffers();
        }
    }

    // === Single int commands ===

    public void writeEnable(int cap) {
        ensureCapacity(8);
        writeInt(GLCommand.ENABLE);
        writeInt(cap);
    }

    public void writeDisable(int cap) {
        ensureCapacity(8);
        writeInt(GLCommand.DISABLE);
        writeInt(cap);
    }

    public void writeClear(int mask) {
        ensureCapacity(8);
        writeInt(GLCommand.CLEAR);
        writeInt(mask);
    }

    public void writeClearStencil(int s) {
        ensureCapacity(8);
        writeInt(GLCommand.CLEAR_STENCIL);
        writeInt(s);
    }

    public void writeCullFace(int mode) {
        ensureCapacity(8);
        writeInt(GLCommand.CULL_FACE);
        writeInt(mode);
    }

    public void writeDepthFunc(int func) {
        ensureCapacity(8);
        writeInt(GLCommand.DEPTH_FUNC);
        writeInt(func);
    }

    public void writeShadeModel(int mode) {
        ensureCapacity(8);
        writeInt(GLCommand.SHADE_MODEL);
        writeInt(mode);
    }

    public void writeLogicOp(int opcode) {
        ensureCapacity(8);
        writeInt(GLCommand.LOGIC_OP);
        writeInt(opcode);
    }

    public void writeMatrixMode(int mode) {
        ensureCapacity(8);
        writeInt(GLCommand.MATRIX_MODE);
        writeInt(mode);
    }

    public void writeActiveTexture(int texture) {
        ensureCapacity(8);
        writeInt(GLCommand.ACTIVE_TEXTURE);
        writeInt(texture);
    }

    public void writeUseProgram(int program) {
        ensureCapacity(8);
        writeInt(GLCommand.USE_PROGRAM);
        writeInt(program);
    }

    public void writePushAttrib(int mask) {
        ensureCapacity(8);
        writeInt(GLCommand.PUSH_ATTRIB);
        writeInt(mask);
    }

    public void writePopAttrib() {
        ensureCapacity(8);
        writeInt(GLCommand.POP_ATTRIB);
        writeInt(0); // unused padding
    }

    public void writeLoadIdentity() {
        ensureCapacity(4);
        writeInt(GLCommand.LOAD_IDENTITY);
    }

    public void writePushMatrix() {
        ensureCapacity(4);
        writeInt(GLCommand.PUSH_MATRIX);
    }

    public void writePopMatrix() {
        ensureCapacity(4);
        writeInt(GLCommand.POP_MATRIX);
    }

    public void writeStencilMask(int mask) {
        ensureCapacity(8);
        writeInt(GLCommand.STENCIL_MASK);
        writeInt(mask);
    }

    public void writeDepthMask(boolean flag) {
        ensureCapacity(8);
        writeInt(GLCommand.DEPTH_MASK);
        writeInt(flag ? 1 : 0);
    }

    public void writeFrontFace(int mode) {
        ensureCapacity(8);
        writeInt(GLCommand.FRONT_FACE);
        writeInt(mode);
    }

    // === Two int commands ===

    public void writeBindTexture(int target, int texture) {
        ensureCapacity(12);
        writeInt(GLCommand.BIND_TEXTURE);
        writeInt(target);
        writeInt(texture);
    }

    public void writePolygonMode(int face, int mode) {
        ensureCapacity(12);
        writeInt(GLCommand.POLYGON_MODE);
        writeInt(face);
        writeInt(mode);
    }

    public void writeColorMaterial(int face, int mode) {
        ensureCapacity(12);
        writeInt(GLCommand.COLOR_MATERIAL);
        writeInt(face);
        writeInt(mode);
    }

    public void writeLineStipple(int factor, int pattern) {
        ensureCapacity(12);
        writeInt(GLCommand.LINE_STIPPLE);
        writeInt(factor);
        writeInt(pattern);
    }

    public void writeStencilMaskSeparate(int face, int mask) {
        ensureCapacity(12);
        writeInt(GLCommand.STENCIL_MASK_SEPARATE);
        writeInt(face);
        writeInt(mask);
    }

    public void writeFogi(int pname, int param) {
        ensureCapacity(12);
        writeInt(GLCommand.FOGI);
        writeInt(pname);
        writeInt(param);
    }

    public void writeHint(int target, int mode) {
        ensureCapacity(12);
        writeInt(GLCommand.HINT);
        writeInt(target);
        writeInt(mode);
    }

    // === Three int commands ===

    public void writeStencilFunc(int func, int ref, int mask) {
        ensureCapacity(16);
        writeInt(GLCommand.STENCIL_FUNC);
        writeInt(func);
        writeInt(ref);
        writeInt(mask);
    }

    public void writeStencilOp(int fail, int zfail, int zpass) {
        ensureCapacity(16);
        writeInt(GLCommand.STENCIL_OP);
        writeInt(fail);
        writeInt(zfail);
        writeInt(zpass);
    }

    public void writeTexParameteri(int target, int pname, int param) {
        ensureCapacity(16);
        writeInt(GLCommand.TEX_PARAMETERI);
        writeInt(target);
        writeInt(pname);
        writeInt(param);
    }

    // === Four int commands ===

    public void writeViewport(int x, int y, int width, int height) {
        ensureCapacity(20);
        writeInt(GLCommand.VIEWPORT);
        writeInt(x);
        writeInt(y);
        writeInt(width);
        writeInt(height);
    }

    public void writeScissor(int x, int y, int width, int height) {
        ensureCapacity(20);
        writeInt(GLCommand.SCISSOR);
        writeInt(x);
        writeInt(y);
        writeInt(width);
        writeInt(height);
    }

    public void writeBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        ensureCapacity(20);
        writeInt(GLCommand.BLEND_FUNC);
        writeInt(srcRgb);
        writeInt(dstRgb);
        writeInt(srcAlpha);
        writeInt(dstAlpha);
    }

    public void writeColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        ensureCapacity(20);
        writeInt(GLCommand.COLOR_MASK);
        writeInt(red ? 1 : 0);
        writeInt(green ? 1 : 0);
        writeInt(blue ? 1 : 0);
        writeInt(alpha ? 1 : 0);
    }

    public void writeStencilFuncSeparate(int face, int func, int ref, int mask) {
        ensureCapacity(20);
        writeInt(GLCommand.STENCIL_FUNC_SEPARATE);
        writeInt(face);
        writeInt(func);
        writeInt(ref);
        writeInt(mask);
    }

    public void writeStencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        ensureCapacity(20);
        writeInt(GLCommand.STENCIL_OP_SEPARATE);
        writeInt(face);
        writeInt(sfail);
        writeInt(dpfail);
        writeInt(dppass);
    }

    // === Float commands ===

    public void writePointSize(float size) {
        ensureCapacity(8);
        writeInt(GLCommand.POINT_SIZE);
        writeFloat(size);
    }

    public void writeLineWidth(float width) {
        ensureCapacity(8);
        writeInt(GLCommand.LINE_WIDTH);
        writeFloat(width);
    }

    public void writePolygonOffset(float factor, float units) {
        ensureCapacity(12);
        writeInt(GLCommand.POLYGON_OFFSET);
        writeFloat(factor);
        writeFloat(units);
    }

    public void writeColor(float r, float g, float b, float a) {
        ensureCapacity(20);
        writeInt(GLCommand.COLOR);
        writeFloat(r);
        writeFloat(g);
        writeFloat(b);
        writeFloat(a);
    }

    public void writeClearColor(float r, float g, float b, float a) {
        ensureCapacity(20);
        writeInt(GLCommand.CLEAR_COLOR);
        writeFloat(r);
        writeFloat(g);
        writeFloat(b);
        writeFloat(a);
    }

    public void writeClearDepth(double depth) {
        ensureCapacity(12);
        writeInt(GLCommand.CLEAR_DEPTH);
        writeDouble(depth);
    }

    public void writeBlendColor(float r, float g, float b, float a) {
        ensureCapacity(20);
        writeInt(GLCommand.BLEND_COLOR);
        writeFloat(r);
        writeFloat(g);
        writeFloat(b);
        writeFloat(a);
    }

    // === Mixed int+float commands ===

    public void writeAlphaFunc(int func, float ref) {
        ensureCapacity(12);
        writeInt(GLCommand.ALPHA_FUNC);
        writeInt(func);
        writeFloat(ref);
    }

    public void writeFogf(int pname, float param) {
        ensureCapacity(12);
        writeInt(GLCommand.FOGF);
        writeInt(pname);
        writeFloat(param);
    }

    public void writeLightf(int light, int pname, float param) {
        ensureCapacity(16);
        writeInt(GLCommand.LIGHTF);
        writeInt(light);
        writeInt(pname);
        writeFloat(param);
    }

    public void writeLightModelf(int pname, float param) {
        ensureCapacity(12);
        writeInt(GLCommand.LIGHT_MODELF);
        writeInt(pname);
        writeFloat(param);
    }

    public void writeLighti(int light, int pname, int param) {
        ensureCapacity(16);
        writeInt(GLCommand.LIGHTI);
        writeInt(light);
        writeInt(pname);
        writeInt(param);
    }

    public void writeLightModeli(int pname, int param) {
        ensureCapacity(12);
        writeInt(GLCommand.LIGHT_MODELI);
        writeInt(pname);
        writeInt(param);
    }

    public void writeMaterialf(int face, int pname, float param) {
        ensureCapacity(16);
        writeInt(GLCommand.MATERIALF);
        writeInt(face);
        writeInt(pname);
        writeFloat(param);
    }

    public void writeTexParameterf(int target, int pname, float param) {
        ensureCapacity(16);
        writeInt(GLCommand.TEX_PARAMETERF);
        writeInt(target);
        writeInt(pname);
        writeFloat(param);
    }

    // === Transformation commands ===

    public void writeTranslate(Vector3f translationVector) {
        ensureCapacity(16);
        writeInt(GLCommand.TRANSLATE);
        writeFloat(translationVector.x);
        writeFloat(translationVector.y);
        writeFloat(translationVector.z);
    }

    public void writeScale(Vector3f scaleVector) {
        ensureCapacity(16);
        writeInt(GLCommand.SCALE);
        writeFloat(scaleVector.x);
        writeFloat(scaleVector.y);
        writeFloat(scaleVector.z);
    }

    // === Matrix commands ===

    public void writeMultMatrix(Matrix4f matrix) {
        ensureCapacity(68);
        writeInt(GLCommand.MULT_MATRIX);
        writeMatrix4f(matrix);
    }

    public void writeLoadMatrix(Matrix4f matrix) {
        ensureCapacity(68);
        writeInt(GLCommand.LOAD_MATRIX);
        writeMatrix4f(matrix);
    }

    /**
     * Write a Matrix4f to the buffer in column-major order (OpenGL convention).
     * Uses accessor methods instead of getToAddress() to avoid JOML MemUtil issues.
     */
    private void writeMatrix4f(Matrix4f m) {
        // Column 0
        writeFloat(m.m00()); writeFloat(m.m01()); writeFloat(m.m02()); writeFloat(m.m03());
        // Column 1
        writeFloat(m.m10()); writeFloat(m.m11()); writeFloat(m.m12()); writeFloat(m.m13());
        // Column 2
        writeFloat(m.m20()); writeFloat(m.m21()); writeFloat(m.m22()); writeFloat(m.m23());
        // Column 3
        writeFloat(m.m30()); writeFloat(m.m31()); writeFloat(m.m32()); writeFloat(m.m33());
    }

    // === Buffer commands (inline 4 floats) ===

    public void writeFog(int pname, FloatBuffer params) {
        final int count = Math.min(params.remaining(), 4);
        ensureCapacity(12 + count * 4);
        writeInt(GLCommand.FOG);
        writeInt(pname);
        writeInt(count);
        final int pos = params.position();
        for (int i = 0; i < count; i++) {
            writeFloat(params.get(pos + i));
        }
    }

    public void writeLight(int light, int pname, FloatBuffer params) {
        final int count = Math.min(params.remaining(), 4);
        ensureCapacity(16 + count * 4);
        writeInt(GLCommand.LIGHT);
        writeInt(light);
        writeInt(pname);
        writeInt(count);
        final int pos = params.position();
        for (int i = 0; i < count; i++) {
            writeFloat(params.get(pos + i));
        }
    }

    public void writeLightModel(int pname, FloatBuffer params) {
        final int count = Math.min(params.remaining(), 4);
        ensureCapacity(12 + count * 4);
        writeInt(GLCommand.LIGHT_MODEL);
        writeInt(pname);
        writeInt(count);
        final int pos = params.position();
        for (int i = 0; i < count; i++) {
            writeFloat(params.get(pos + i));
        }
    }

    public void writeMaterial(int face, int pname, FloatBuffer params) {
        final int count = Math.min(params.remaining(), 4);
        ensureCapacity(16 + count * 4);
        writeInt(GLCommand.MATERIAL);
        writeInt(face);
        writeInt(pname);
        writeInt(count);
        final int pos = params.position();
        for (int i = 0; i < count; i++) {
            writeFloat(params.get(pos + i));
        }
    }

    public void writeClipPlane(int plane, double a, double b, double c, double d) {
        ensureCapacity(40);
        writeInt(GLCommand.CLIP_PLANE);
        writeInt(plane);
        writeDouble(a);
        writeDouble(b);
        writeDouble(c);
        writeDouble(d);
    }

    // === Draw commands ===

    public void writeDrawRange(int vboIndex) {
        ensureCapacity(8);
        writeInt(GLCommand.DRAW_RANGE);
        writeInt(vboIndex);
    }

    /**
     * Write a DRAW_RANGE_RESTORE command that draws a VBO range and restores GL current state after.
     * Used for immediate mode VBOs to restore GL_CURRENT_COLOR, GL_CURRENT_NORMAL, GL_CURRENT_TEXTURE_COORDS.
     *
     * @param vboIndex Index into ownedVbos array
     */
    public void writeDrawRangeRestore(
        int vboIndex, AccumulatedDraw.RestoreData restoreData
    ) {
        ensureCapacity(28);
        writeInt(GLCommand.DRAW_RANGE_RESTORE);
        writeInt(vboIndex);
        writeInt(restoreData.flags);
        // Last color (4 bytes)
        writeInt(restoreData.lastColor);
        // Last normal (4 bytes)
        writeInt(restoreData.lastNormal);
        // Last texcoord (2 floats = 8 bytes)
        writeFloat(restoreData.lastTexCoordU);
        writeFloat(restoreData.lastTexCoordV);
    }


    public void writeCallList(int listId) {
        ensureCapacity(8);
        writeInt(GLCommand.CALL_LIST);
        writeInt(listId);
    }

    public void writeBindVBO(int vbo) {
        ensureCapacity(8);
        writeInt(GLCommand.BIND_VBO);
        writeInt(vbo);
    }

    public void writeBindVAO(int vao) {
        ensureCapacity(8);
        writeInt(GLCommand.BIND_VAO);
        writeInt(vao);
    }

    public void writeDrawBuffer(int mode) {
        ensureCapacity(8);
        writeInt(GLCommand.DRAW_BUFFER);
        writeInt(mode);
    }

    private static final int MAX_DRAW_BUFFERS = 8;
    // cmd (4) + count (4) + buffers (4 * MAX_DRAW_BUFFERS)
    private static final int DRAW_BUFFERS_SIZE = 4 + 4 + 4 * MAX_DRAW_BUFFERS;

    public void writeDrawBuffers(int count, int buffer) {
        ensureCapacity(DRAW_BUFFERS_SIZE);
        writeInt(GLCommand.DRAW_BUFFERS);
        writeInt(count);
        writeInt(buffer);
        for (int i = 1; i < MAX_DRAW_BUFFERS; i++) {
            writeInt(0);
        }
    }

    public void writeDrawBuffers(int count, IntBuffer bufs) {
        ensureCapacity(DRAW_BUFFERS_SIZE);
        writeInt(GLCommand.DRAW_BUFFERS);
        writeInt(count);
        final int pos = bufs.position();
        for (int i = 0; i < count && i < MAX_DRAW_BUFFERS; i++) {
            writeInt(bufs.get(pos + i));
        }
        for (int i = count; i < MAX_DRAW_BUFFERS; i++) {
            writeInt(0);
        }
    }

    // === Complex object reference ===

    public void writeComplexCommand(DisplayListCommand cmd) {
        ensureCapacity(8);
        final int index = complexObjects.size();
        complexObjects.add(cmd);
        writeInt(GLCommand.COMPLEX_REF);
        writeInt(index);
    }

    // === Lifecycle ===

    public int size() {
        return (int) (writePointer - basePointer);
    }

    public void delete() {
        for (IndexedDrawCapture c : indexedDraws.getCaptures()) {
            c.freeBuffers();
        }
        for (DisplayListCommand cmd : complexObjects) {
            cmd.delete();
        }
        complexObjects.clear();
        if (buffer != null) {
            memFree(buffer);
            buffer = null;
        }
    }

    /**
     * Deletes every allocated object & returns a ByteBuffer with the capacity equal to its limit.
     * This CommandRecorder object cannot be used after calling this anymore
     */
    public ByteBuffer finish() {
        for (IndexedDrawCapture c : indexedDraws.getCaptures()) {
            c.freeBuffers();
        }
        final int size = size();
        buffer.limit(size);
        final ByteBuffer out = memRealloc(buffer, size);
        buffer = null;
        complexObjects.clear();
        return out;
    }

    public DisplayListCommand[] getComplexObjects() {
        return complexObjects.toArray(new DisplayListCommand[complexObjects.size()]);
    }

    public DisplayListCommand getComplexObject(int index) {
        return complexObjects.get(index);
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Clear the buffer for reuse. Does NOT free memory.
     */
    public void clear() {
        writePointer = basePointer;
        complexObjects.clear();
    }
}
