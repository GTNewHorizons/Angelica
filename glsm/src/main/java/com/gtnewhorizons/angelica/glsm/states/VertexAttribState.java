package com.gtnewhorizons.angelica.glsm.states;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/**
 * Per-VAO vertex attribute pointer state tracking (Mesa-style).
 */
public class VertexAttribState {
    public static final int MAX_ATTRIBS = 16;

    private static final Int2ObjectOpenHashMap<Attrib[]> vaoAttribs = new Int2ObjectOpenHashMap<>();
    private static Attrib[] current;
    private static final ArrayDeque<Attrib[]> pool = new ArrayDeque<>();

    public static void init(int defaultVAO) {
        vaoAttribs.clear();
        pool.clear();
        current = allocAttribArray();
        vaoAttribs.put(defaultVAO, current);
    }

    public static void onBindVertexArray(int newVAO) {
        current = vaoAttribs.computeIfAbsent(newVAO, k -> allocAttribArray());
    }

    public static void onDeleteVertexArray(int vaoId) {
        final Attrib[] arr = vaoAttribs.remove(vaoId);
        if (arr != null) {
            pool.addLast(arr);
        }
    }

    public static void reset() {
        vaoAttribs.clear();
        pool.clear();
        current = null;
    }

    private static Attrib[] allocAttribArray() {
        Attrib[] arr = pool.pollFirst();
        if (arr != null) {
            for (Attrib a : arr) a.reset();
            return arr;
        }
        arr = new Attrib[MAX_ATTRIBS];
        for (int i = 0; i < MAX_ATTRIBS; i++) arr[i] = new Attrib();
        return arr;
    }

    public static void set(int index, int size, int type, boolean normalized, int stride, long offset, int vboId) {
        if (index < 0 || index >= MAX_ATTRIBS) return;
        final Attrib a = current[index];
        a.size = size;
        a.type = type;
        a.normalized = normalized;
        a.stride = stride;
        a.offset = offset;
        a.vboId = vboId;
        a.clientPointer = null;
    }

    public static void set(int index, int size, int type, boolean normalized, int stride, ByteBuffer pointer, int vboId) {
        if (index < 0 || index >= MAX_ATTRIBS) return;
        final Attrib a = current[index];
        a.size = size;
        a.type = type;
        a.normalized = normalized;
        a.stride = stride;
        a.offset = 0;
        a.vboId = vboId;
        a.clientPointer = (vboId == 0) ? pointer : null;
    }

    public static void setEnabled(int index, boolean enabled) {
        if (index < 0 || index >= MAX_ATTRIBS) return;
        current[index].enabled = enabled;
    }

    public static Attrib get(int index) {
        return current[index];
    }

    public static boolean hasVBOBoundAttrib() {
        for (int i = 0; i < MAX_ATTRIBS; i++) {
            if (current[i].enabled && current[i].vboId != 0) return true;
        }
        return false;
    }

    public static class Attrib {
        public boolean enabled;
        public int size;
        public int type;
        public boolean normalized;
        public int stride;
        public long offset;
        public int vboId;
        public ByteBuffer clientPointer;

        public void reset() {
            enabled = false;
            size = 0;
            type = 0;
            normalized = false;
            stride = 0;
            offset = 0;
            vboId = 0;
            clientPointer = null;
        }

        public int effectiveStride() {
            return (stride != 0) ? stride : size * typeSizeBytes();
        }

        public int typeSizeBytes() {
            return glTypeSizeBytes(type);
        }

        public static int glTypeSizeBytes(int glType) {
            return switch (glType) {
                case GL11.GL_FLOAT, GL11.GL_INT, GL11.GL_UNSIGNED_INT -> 4;
                case GL11.GL_DOUBLE -> 8;
                case GL11.GL_SHORT, GL11.GL_UNSIGNED_SHORT -> 2;
                case GL11.GL_BYTE, GL11.GL_UNSIGNED_BYTE -> 1;
                default -> 4;
            };
        }

        public float readComponent(ByteBuffer buf, int base, int component) {
            return switch (type) {
                case GL11.GL_FLOAT -> buf.getFloat(base + component * 4);
                case GL11.GL_DOUBLE -> (float) buf.getDouble(base + component * 8);
                case GL11.GL_INT -> {
                    final int v = buf.getInt(base + component * 4);
                    yield normalized ? v / (float) Integer.MAX_VALUE : (float) v;
                }
                case GL11.GL_UNSIGNED_INT -> {
                    final int v = buf.getInt(base + component * 4);
                    yield normalized ? (v & 0xFFFFFFFFL) / (float) 0xFFFFFFFFL : (float) (v & 0xFFFFFFFFL);
                }
                case GL11.GL_SHORT -> {
                    final short v = buf.getShort(base + component * 2);
                    yield normalized ? v / (float) Short.MAX_VALUE : (float) v;
                }
                case GL11.GL_UNSIGNED_SHORT -> {
                    final int v = buf.getShort(base + component * 2) & 0xFFFF;
                    yield normalized ? v / (float) 0xFFFF : (float) v;
                }
                case GL11.GL_BYTE -> {
                    final byte v = buf.get(base + component);
                    yield normalized ? v / 127.0f : (float) v;
                }
                case GL11.GL_UNSIGNED_BYTE -> {
                    final int v = buf.get(base + component) & 0xFF;
                    yield normalized ? v / 255.0f : (float) v;
                }
                default -> 0.0f;
            };
        }
    }
}
