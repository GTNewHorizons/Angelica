package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.*;

public final class CommandBuffer {
    private static final int DEFAULT_CAPACITY = 4096; // Guess at typical display list size

    private ByteBuffer buffer;
    private long basePointer;
    private long writePointer;
    private final List<Object> complexObjects = new ArrayList<>();

    public CommandBuffer() {
        this(DEFAULT_CAPACITY);
    }

    public CommandBuffer(int initialCapacity) {
        this.buffer = memAlloc(initialCapacity);
        this.basePointer = memAddress(buffer);
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
            basePointer = memAddress(buffer);
            writePointer = basePointer + size;
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

    // === Double commands ===

    public void writeTranslate(double x, double y, double z) {
        ensureCapacity(28);
        writeInt(GLCommand.TRANSLATE);
        writeDouble(x);
        writeDouble(y);
        writeDouble(z);
    }

    public void writeRotate(double angle, double x, double y, double z) {
        ensureCapacity(36);
        writeInt(GLCommand.ROTATE);
        writeDouble(angle);
        writeDouble(x);
        writeDouble(y);
        writeDouble(z);
    }

    public void writeScale(double x, double y, double z) {
        ensureCapacity(28);
        writeInt(GLCommand.SCALE);
        writeDouble(x);
        writeDouble(y);
        writeDouble(z);
    }

    public void writeOrtho(double left, double right, double bottom, double top, double zNear, double zFar) {
        ensureCapacity(52);
        writeInt(GLCommand.ORTHO);
        writeDouble(left);
        writeDouble(right);
        writeDouble(bottom);
        writeDouble(top);
        writeDouble(zNear);
        writeDouble(zFar);
    }

    public void writeFrustum(double left, double right, double bottom, double top, double zNear, double zFar) {
        ensureCapacity(52);
        writeInt(GLCommand.FRUSTUM);
        writeDouble(left);
        writeDouble(right);
        writeDouble(bottom);
        writeDouble(top);
        writeDouble(zNear);
        writeDouble(zFar);
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
        writeFloat(restoreData.lastTexCoordU);
    }

    public void writeCallList(int listId) {
        ensureCapacity(8);
        writeInt(GLCommand.CALL_LIST);
        writeInt(listId);
    }

    public void writeDrawArrays(int mode, int start, int count) {
        ensureCapacity(16);
        writeInt(GLCommand.DRAW_ARRAYS);
        writeInt(mode);
        writeInt(start);
        writeInt(count);
    }

    public void writeDrawElements(int mode, int indices_count, int type, long indices_buffer_offset) {
        ensureCapacity(24);
        writeInt(GLCommand.DRAW_ELEMENTS);
        writeInt(mode);
        writeInt(indices_count);
        writeInt(type);
        writeLong(indices_buffer_offset);
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

    public void writeComplexRef(DisplayListCommand cmd) {
        ensureCapacity(8);
        final int index = complexObjects.size();
        complexObjects.add(cmd);
        writeInt(GLCommand.COMPLEX_REF);
        writeInt(index);
    }

    // === Reading methods for optimization pass ===

    private long readPointer = 0;

    /**
     * Reset read position to the beginning of the buffer.
     */
    public void resetRead() {
        readPointer = basePointer;
    }

    /**
     * Check if there's more data to read.
     */
    public boolean hasRemaining() {
        return readPointer < writePointer;
    }

    /**
     * Read an int from the current position.
     */
    public int readInt() {
        final int value = memGetInt(readPointer);
        readPointer += 4;
        return value;
    }

    public long readLong() {
        final long value = memGetLong(readPointer);
        readPointer += 8;
        return value;
    }

    /**
     * Read a float from the current position.
     */
    public float readFloat() {
        final float value = memGetFloat(readPointer);
        readPointer += 4;
        return value;
    }

    /**
     * Read a double from the current position.
     */
    public double readDouble() {
        final double value = memGetDouble(readPointer);
        readPointer += 8;
        return value;
    }

    /**
     * Read a Matrix4f from the current position (16 floats in column-major order).
     */
    public Matrix4f readMatrix4f(Matrix4f dest) {
        dest.m00(readFloat()); dest.m01(readFloat()); dest.m02(readFloat()); dest.m03(readFloat());
        dest.m10(readFloat()); dest.m11(readFloat()); dest.m12(readFloat()); dest.m13(readFloat());
        dest.m20(readFloat()); dest.m21(readFloat()); dest.m22(readFloat()); dest.m23(readFloat());
        dest.m30(readFloat()); dest.m31(readFloat()); dest.m32(readFloat()); dest.m33(readFloat());
        return dest;
    }

    // === Lifecycle ===

    public int size() {
        return (int) (writePointer - basePointer);
    }

    public long address() {
        return basePointer;
    }

    public ByteBuffer toBuffer() {
        buffer.limit(size());
        buffer.position(0);
        return buffer;
    }

    public Object[] getComplexObjects() {
        return complexObjects.toArray();
    }

    public Object getComplexObject(int index) {
        return complexObjects.get(index);
    }

    public boolean isEmpty() {
        return size() == 0 && complexObjects.isEmpty();
    }

    /**
     * Clear the buffer for reuse. Does NOT free memory.
     */
    public void clear() {
        writePointer = basePointer;
        complexObjects.clear();
    }

    /**
     * Free the underlying buffer. Must be called when done.
     */
    public void free() {
        if (buffer != null) {
            memFree(buffer);
            buffer = null;
            basePointer = 0;
            writePointer = 0;
        }
    }
}
