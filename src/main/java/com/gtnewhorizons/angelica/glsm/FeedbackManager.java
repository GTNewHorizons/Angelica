package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.states.ViewportState;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * Software emulation of GL feedback mode
 */
public class FeedbackManager {

    private static final int FB_3D = 0x01;
    private static final int FB_4D = 0x02;
    private static final int FB_COLOR = 0x04;
    private static final int FB_TEXTURE = 0x08;

    private static int renderMode = GL11.GL_RENDER;
    private static FloatBuffer feedbackBuffer;
    private static int feedbackBufferSize;
    private static int feedbackMask;
    private static int feedbackCount;

    private static final Matrix4f mvpMatrix = new Matrix4f();
    private static final Vector4f clipVec = new Vector4f();

    private static ByteBuffer readbackBuf = ByteBuffer.allocateDirect(256 * 1024).order(ByteOrder.nativeOrder());
    private static ByteBuffer indexReadbackBuf = ByteBuffer.allocateDirect(4 * 1024).order(ByteOrder.nativeOrder());
    private static final IntBuffer attribBuf = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asIntBuffer();

    @FunctionalInterface
    private interface IndexReader {
        int read(int i);
    }

    public static boolean isFeedbackMode() {
        return renderMode == GL11.GL_FEEDBACK;
    }

    public static void glFeedbackBuffer(int type, FloatBuffer buffer) {
        if (renderMode == GL11.GL_FEEDBACK) {
            AngelicaTweaker.LOGGER.error("glFeedbackBuffer called while already in feedback mode");
            return;
        }
        feedbackBuffer = buffer;
        feedbackBufferSize = buffer.remaining();
        feedbackMask = computeMask(type);
        feedbackCount = 0;
    }

    public static int glRenderMode(int mode) {
        int result = 0;
        switch (renderMode) {
            case GL11.GL_FEEDBACK:
                result = (feedbackCount > feedbackBufferSize) ? -1 : feedbackCount;
                break;
            case GL11.GL_RENDER:
            case GL11.GL_SELECT:
                result = 0;
                break;
        }

        if (mode == GL11.GL_SELECT) {
            AngelicaTweaker.LOGGER.warn("glRenderMode(GL_SELECT): selection mode not emulated");
        }

        feedbackCount = 0;
        renderMode = mode;
        return result;
    }

    public static void glPassThrough(float token) {
        if (renderMode == GL11.GL_FEEDBACK) {
            feedbackToken(GL11.GL_PASS_THROUGH_TOKEN);
            feedbackToken(token);
        }
    }

    private static void feedbackToken(float token) {
        if (feedbackCount < feedbackBufferSize) {
            feedbackBuffer.put(feedbackBuffer.position() + feedbackCount, token);
        }
        feedbackCount++;
    }

    private static void feedbackVertex(float winX, float winY, float winZ, float winW) {
        feedbackToken(winX);
        feedbackToken(winY);
        if ((feedbackMask & FB_3D) != 0) feedbackToken(winZ);
        if ((feedbackMask & FB_4D) != 0) feedbackToken(winW);
        if ((feedbackMask & FB_COLOR) != 0) {
            feedbackToken(1f);
            feedbackToken(1f);
            feedbackToken(1f);
            feedbackToken(1f);
        }
        if ((feedbackMask & FB_TEXTURE) != 0) {
            feedbackToken(0f);
            feedbackToken(0f);
            feedbackToken(0f);
            feedbackToken(1f);
        }
    }

    private static void transformAndEmitVertex(float x, float y, float z) {
        clipVec.set(x, y, z, 1.0f);
        mvpMatrix.transform(clipVec);

        final float w = clipVec.w;
        final float ndcX = clipVec.x / w;
        final float ndcY = clipVec.y / w;
        final float ndcZ = clipVec.z / w;

        final ViewportState vp = GLStateManager.getViewportState();
        final float winX = vp.x + vp.width * (ndcX + 1.0f) * 0.5f;
        final float winY = vp.y + vp.height * (ndcY + 1.0f) * 0.5f;
        final float winZ = (float) ((vp.depthRangeFar - vp.depthRangeNear) * ndcZ
            + (vp.depthRangeFar + vp.depthRangeNear)) * 0.5f;
        final float winW = 1.0f / w;

        feedbackVertex(winX, winY, winZ, winW);
    }

    public static void processDrawArrays(int mode, int first, int count) {
        computeMVP();
        int stride = queryPositionStride();
        if (stride == 0) stride = 12; // tightly packed 3 floats

        final long posOffset = queryPositionOffset();
        final int byteCount = count * stride;
        ensureReadbackCapacity(byteCount);
        readbackBuf.clear().limit(byteCount);
        GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, (long) first * stride + posOffset, readbackBuf);

        emitAllPrimitives(mode, count, i -> i, stride);
    }

    public static void processDrawElements(int mode, int indexCount, int type, long eboOffset) {
        computeMVP();
        int stride = queryPositionStride();
        if (stride == 0) stride = 12;

        final int elementSize = elementSizeForType(type);
        final int indexByteCount = indexCount * elementSize;
        ensureIndexReadbackCapacity(indexByteCount);
        indexReadbackBuf.clear().limit(indexByteCount);
        GL15.glGetBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, eboOffset, indexReadbackBuf);
        final ByteBuffer indexBuf = indexReadbackBuf;

        int minIdx = Integer.MAX_VALUE, maxIdx = Integer.MIN_VALUE;
        for (int i = 0; i < indexCount; i++) {
            final int idx = readIndex(indexBuf, type, i);
            if (idx < minIdx) minIdx = idx;
            if (idx > maxIdx) maxIdx = idx;
        }

        final long posOffset = queryPositionOffset();
        final int vertexCount = maxIdx - minIdx + 1;
        final int byteCount = vertexCount * stride;
        ensureReadbackCapacity(byteCount);
        readbackBuf.clear().limit(byteCount);
        GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, (long) minIdx * stride + posOffset, readbackBuf);

        final int base = minIdx;
        emitAllPrimitives(mode, indexCount, i -> readIndex(indexBuf, type, i) - base, stride);
    }

    public static void processDrawElements(int mode, ByteBuffer indices) {
        final int basePos = indices.position();
        processDrawElementsImpl(mode, indices.remaining(), i -> indices.get(basePos + i) & 0xFF);
    }

    public static void processDrawElements(int mode, ShortBuffer indices) {
        final int basePos = indices.position();
        processDrawElementsImpl(mode, indices.remaining(), i -> indices.get(basePos + i) & 0xFFFF);
    }

    public static void processDrawElements(int mode, IntBuffer indices) {
        final int basePos = indices.position();
        processDrawElementsImpl(mode, indices.remaining(), i -> indices.get(basePos + i));
    }

    public static void processDrawElements(int mode, int count, int type, ByteBuffer indices) {
        final int pos = indices.position();
        final IndexReader reader = switch (type) {
            case GL11.GL_UNSIGNED_BYTE -> i -> indices.get(pos + i) & 0xFF;
            case GL11.GL_UNSIGNED_SHORT -> i -> indices.getShort(pos + i * 2) & 0xFFFF;
            case GL11.GL_UNSIGNED_INT -> i -> indices.getInt(pos + i * 4);
            default -> i -> 0;
        };
        processDrawElementsImpl(mode, count, reader);
    }

    private static void processDrawElementsImpl(int mode, int count, IndexReader reader) {
        computeMVP();
        int stride = queryPositionStride();
        if (stride == 0) stride = 12;

        int minIdx = Integer.MAX_VALUE, maxIdx = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            final int idx = reader.read(i);
            if (idx < minIdx) minIdx = idx;
            if (idx > maxIdx) maxIdx = idx;
        }

        readVerticesFromVBO(minIdx, maxIdx, stride);
        final int base = minIdx;
        emitAllPrimitives(mode, count, i -> reader.read(i) - base, stride);
    }

    private static void computeMVP() {
        mvpMatrix.set(GLStateManager.getProjectionMatrix()).mul(GLStateManager.getModelViewMatrix());
    }

    private static int queryPositionStride() {
        attribBuf.clear();
        GL20.glGetVertexAttrib(0, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE, attribBuf);
        return attribBuf.get(0);
    }

    private static int computeMask(int type) {
        return switch (type) {
            case GL11.GL_2D -> 0;
            case GL11.GL_3D -> FB_3D;
            case GL11.GL_3D_COLOR -> FB_3D | FB_COLOR;
            case GL11.GL_3D_COLOR_TEXTURE -> FB_3D | FB_COLOR | FB_TEXTURE;
            case GL11.GL_4D_COLOR_TEXTURE -> FB_3D | FB_4D | FB_COLOR | FB_TEXTURE;
            default -> 0;
        };
    }

    private static int elementSizeForType(int type) {
        return switch (type) {
            case GL11.GL_UNSIGNED_BYTE -> 1;
            case GL11.GL_UNSIGNED_SHORT -> 2;
            case GL11.GL_UNSIGNED_INT -> 4;
            default -> 4;
        };
    }

    private static int readIndex(ByteBuffer buf, int type, int i) {
        return switch (type) {
            case GL11.GL_UNSIGNED_BYTE -> buf.get(i) & 0xFF;
            case GL11.GL_UNSIGNED_SHORT -> buf.getShort(i * 2) & 0xFFFF;
            case GL11.GL_UNSIGNED_INT -> buf.getInt(i * 4);
            default -> 0;
        };
    }

    private static void ensureReadbackCapacity(int bytes) {
        if (readbackBuf.capacity() < bytes) {
            readbackBuf = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        }
    }

    private static void ensureIndexReadbackCapacity(int bytes) {
        if (indexReadbackBuf.capacity() < bytes) {
            indexReadbackBuf = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        }
    }

    private static long queryPositionOffset() {
        final ByteBuffer buf = GL20.glGetVertexAttribPointer(0, GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER, 8);
        return buf == null ? 0L : buf.getLong(0);
    }

    private static void readVerticesFromVBO(int minIdx, int maxIdx, int stride) {
        final long posOffset = queryPositionOffset();
        final int vertexCount = maxIdx - minIdx + 1;
        final int byteCount = vertexCount * stride;
        ensureReadbackCapacity(byteCount);
        readbackBuf.clear().limit(byteCount);
        GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, (long) minIdx * stride + posOffset, readbackBuf);
    }

    private static void emitVertex(int vertexIndex, int stride) {
        final float x = readbackBuf.getFloat(vertexIndex * stride);
        final float y = readbackBuf.getFloat(vertexIndex * stride + 4);
        final float z = readbackBuf.getFloat(vertexIndex * stride + 8);
        transformAndEmitVertex(x, y, z);
    }

    private static void emitAllPrimitives(int mode, int count, IndexReader reader, int stride) {
        switch (mode) {
            case GL11.GL_POINTS:
                for (int i = 0; i < count; i++) {
                    feedbackToken(GL11.GL_POINT_TOKEN);
                    emitVertex(reader.read(i), stride);
                }
                break;

            case GL11.GL_LINES:
                for (int i = 0; i + 1 < count; i += 2) {
                    feedbackToken(i == 0 ? GL11.GL_LINE_RESET_TOKEN : GL11.GL_LINE_TOKEN);
                    emitVertex(reader.read(i), stride);
                    emitVertex(reader.read(i + 1), stride);
                }
                break;

            case GL11.GL_LINE_STRIP:
                for (int i = 0; i + 1 < count; i++) {
                    feedbackToken(i == 0 ? GL11.GL_LINE_RESET_TOKEN : GL11.GL_LINE_TOKEN);
                    emitVertex(reader.read(i), stride);
                    emitVertex(reader.read(i + 1), stride);
                }
                break;

            case GL11.GL_LINE_LOOP:
                for (int i = 0; i + 1 < count; i++) {
                    feedbackToken(i == 0 ? GL11.GL_LINE_RESET_TOKEN : GL11.GL_LINE_TOKEN);
                    emitVertex(reader.read(i), stride);
                    emitVertex(reader.read(i + 1), stride);
                }
                if (count > 2) {
                    feedbackToken(GL11.GL_LINE_TOKEN);
                    emitVertex(reader.read(count - 1), stride);
                    emitVertex(reader.read(0), stride);
                }
                break;

            case GL11.GL_TRIANGLES:
                for (int i = 0; i + 2 < count; i += 3) {
                    feedbackToken(GL11.GL_POLYGON_TOKEN);
                    feedbackToken(3);
                    emitVertex(reader.read(i), stride);
                    emitVertex(reader.read(i + 1), stride);
                    emitVertex(reader.read(i + 2), stride);
                }
                break;

            case GL11.GL_TRIANGLE_STRIP:
                for (int i = 0; i + 2 < count; i++) {
                    feedbackToken(GL11.GL_POLYGON_TOKEN);
                    feedbackToken(3);
                    if ((i & 1) == 0) {
                        emitVertex(reader.read(i), stride);
                        emitVertex(reader.read(i + 1), stride);
                    } else {
                        emitVertex(reader.read(i + 1), stride);
                        emitVertex(reader.read(i), stride);
                    }
                    emitVertex(reader.read(i + 2), stride);
                }
                break;

            case GL11.GL_TRIANGLE_FAN:
                for (int i = 1; i + 1 < count; i++) {
                    feedbackToken(GL11.GL_POLYGON_TOKEN);
                    feedbackToken(3);
                    emitVertex(reader.read(0), stride);
                    emitVertex(reader.read(i), stride);
                    emitVertex(reader.read(i + 1), stride);
                }
                break;

            case GL11.GL_QUADS:
                for (int i = 0; i + 3 < count; i += 4) {
                    feedbackToken(GL11.GL_POLYGON_TOKEN);
                    feedbackToken(4);
                    emitVertex(reader.read(i), stride);
                    emitVertex(reader.read(i + 1), stride);
                    emitVertex(reader.read(i + 2), stride);
                    emitVertex(reader.read(i + 3), stride);
                }
                break;

            default:
                throw new UnsupportedOperationException("Feedback mode: unsupported primitive mode 0x" + Integer.toHexString(mode));
        }
    }

    static void reset() {
        renderMode = GL11.GL_RENDER;
        feedbackBuffer = null;
        feedbackBufferSize = 0;
        feedbackMask = 0;
        feedbackCount = 0;
    }
}
