package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class VAOManager {
    public static final int MAX_ATTRIBS = 16;

    public static final Int2ObjectOpenHashMap<VAOData> vaoMap = new Int2ObjectOpenHashMap<>();

    private static VAOData current = null;
    private static int currentVertexFlags = 0;
    private static Attrib[] currentAttribs = null;
    public static int boundEBO = 0;

    private static int clientSideEnabledCount = 0;


    public static void init(int defaultVAO) {
        final VAOData data = new VAOData();
        current = data;
        currentAttribs = data.attribs;
        vaoMap.put(defaultVAO, data);
    }

    public static void onBindEBO(int ebo) {
        boundEBO = ebo;
    }

    public static void onBindVertexArrayPre(int vaoId) {
        // Save old VAO data
        if (current != null) {
            current.vertexFlags = currentVertexFlags;
            current.ebo = boundEBO;
            current.attribs = currentAttribs;
        }

        VAOData data = vaoMap.get(vaoId);
        if (data == null) {
            data = new VAOData();
            vaoMap.put(vaoId, data);
        }
        current = data;
        currentVertexFlags = data.vertexFlags;
        currentAttribs = data.attribs;
        boundEBO = data.ebo;

        recomputeClientSideCount();
    }

    public static void onDeleteVertexArray(int vaoId) {
        vaoMap.remove(vaoId);
    }

    public static void enableClientVertexFlag(int flag) { currentVertexFlags |= flag; }
    public static void disableClientVertexFlag(int flag) { currentVertexFlags &= ~flag; }
    public static int getCurrentVertexFlags() { return currentVertexFlags; }
    public static void setCurrentVertexFlags(int flags) { currentVertexFlags = flags; }

    public static final class VAOData {
        public Attrib[] attribs;
        public int vertexFlags;
        public int ebo;

        public VAOData() {
            attribs = new Attrib[MAX_ATTRIBS];
        }
    }

    public static void setAttribute(int index, int size, int type, boolean normalized, int stride, long offset, int vboId) {
        if (index < 0 || index >= MAX_ATTRIBS) return;
        final Attrib a = getAttrib(index);
        final boolean wasClient = a.clientPointer != null;
        a.size = size;
        a.type = type;
        a.normalized = normalized;
        a.stride = stride;
        a.offset = offset;
        a.vboId = vboId;
        a.clientPointer = null;
        a.genericPointer = true;
        if (wasClient && a.enabled) {
            clientSideEnabledCount--;
        }
    }

    public static void setAttribute(int index, int size, int type, boolean normalized, int stride, ByteBuffer pointer) {
        if (index < 0 || index >= MAX_ATTRIBS) return;
        final Attrib a = getAttrib(index);
        final boolean wasClient = a.clientPointer != null;
        a.size = size;
        a.type = type;
        a.normalized = normalized;
        a.stride = stride;
        a.offset = 0;
        a.vboId = 0;
        a.clientPointer = pointer;
        a.genericPointer = true;
        if (!wasClient && a.enabled) {
            clientSideEnabledCount++;
        }
    }

    public static boolean isGenericPointerEnabled(int index) {
        if (index < 0 || index >= MAX_ATTRIBS) return false;
        final Attrib a = currentAttribs[index];
        return a != null && a.genericPointer && a.enabled;
    }

    public static void markConventional(int index) {
        if (index < 0 || index >= MAX_ATTRIBS) return;
        final Attrib a = currentAttribs[index];
        if (a != null) a.genericPointer = false;
    }

    public static void setEnabled(int index, boolean enabled) {
        if (index < 0 || index >= MAX_ATTRIBS) return;
        final Attrib a = getAttrib(index);
        if (a.clientPointer != null && a.enabled != enabled) {
            clientSideEnabledCount += enabled ? 1 : -1;
        }
        a.enabled = enabled;
    }

    public static void enableAttribute(int index) {
        if (index < 0 || index >= MAX_ATTRIBS) return;
        final Attrib a = getAttrib(index);
        if (a.clientPointer != null && !a.enabled) {
            clientSideEnabledCount++;
        }
        a.enabled = true;
    }

    public static void disableAttribute(int index) {
        if (index < 0 || index >= MAX_ATTRIBS) return;
        final Attrib a = getAttrib(index);
        if (a.clientPointer != null && a.enabled) {
            clientSideEnabledCount--;
        }
        a.enabled = false;
    }

    // Lazily allocate each attrib. Most VAOs don't even have 4 attribs, let alone 16.
    private static Attrib getAttrib(int index) {
        Attrib a = currentAttribs[index];
        if (a == null) {
            a = new Attrib();
            currentAttribs[index] = a;
        }
        return a;
    }

    public static boolean isClientSideAttrib(int index) {
        final Attrib attrib = currentAttribs[index];
        return attrib != null && attrib.isClientSide();
    }

    public static int getAttribEffectiveStride(int index) {
        final Attrib attrib = currentAttribs[index];
        return attrib != null ? attrib.effectiveStride() : 0;
    }

    public static long getAttribOffset(int index) {
        final Attrib attrib = currentAttribs[index];
        return attrib != null ? attrib.offset : 0;
    }

    public static int getAttribVBO(int index) {
        final Attrib attrib = currentAttribs[index];
        return attrib != null ? attrib.vboId : 0;
    }

    public static @Nullable Attrib get(int index) {
        return currentAttribs[index];
    }



    private static void recomputeClientSideCount() {
        int count = 0;
        for (int i = 0; i < MAX_ATTRIBS; i++) {
            if (isClientSideAttrib(i)) count++;
        }
        clientSideEnabledCount = count;
    }

    /**
     * Returns true if any currently enabled vertex attribute was registered without a VBO
     * (i.e. as a client-side pointer). In core profile, such attribs are treated as null
     * offsets into a non-existent buffer, causing a native crash at draw time.
     */
    public static boolean hasAnyClientSideEnabledAttrib() {
        return clientSideEnabledCount > 0;
    }

    /**
     * If any enabled vertex attribute uses a client-side pointer (no VBO), upload all such
     * attribs into a shared stream VBO so the draw succeeds under core profile.
     */
    private static int clientArraysVBO = 0;
    private static int clientArraysVBOCapacity = 0;
    private static final int[] clientArraysVBOOffsets = new int[VAOManager.MAX_ATTRIBS];

    public static void uploadClientArraysToVBO() {
        int totalBytes = 0;
        for (int i = 0; i < VAOManager.MAX_ATTRIBS; i++) {
            clientArraysVBOOffsets[i] = -1;
            final VAOManager.Attrib a = VAOManager.get(i);
            if (a == null || !a.enabled || a.clientPointer == null) continue;
            clientArraysVBOOffsets[i] = totalBytes;
            totalBytes += a.clientPointer.remaining();
        }
        if (totalBytes == 0) return;

        if (clientArraysVBO == 0) {
            clientArraysVBO = RENDER_BACKEND.genBuffers();
        }

        final int savedVBO = GLStateManager.getBoundVBO();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, clientArraysVBO);

        if (totalBytes > clientArraysVBOCapacity) {
            int newCap = Math.max(4096, clientArraysVBOCapacity);
            while (newCap < totalBytes) newCap *= 2;
            clientArraysVBOCapacity = newCap;
        }
        RENDER_BACKEND.bufferData(GL15.GL_ARRAY_BUFFER, clientArraysVBOCapacity, GL15.GL_STREAM_DRAW);

        for (int i = 0; i < VAOManager.MAX_ATTRIBS; i++) {
            if (clientArraysVBOOffsets[i] < 0) continue;
            final VAOManager.Attrib a = VAOManager.get(i);
            RENDER_BACKEND.bufferSubData(GL15.GL_ARRAY_BUFFER, clientArraysVBOOffsets[i], a.clientPointer.duplicate());
            RENDER_BACKEND.vertexAttribPointer(i, a.size, a.type, a.normalized, a.stride, (long) clientArraysVBOOffsets[i]);
        }

        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedVBO);
    }


    public static final class Attrib {
        public boolean enabled;
        public int size;
        public int type;
        public boolean normalized;
        public int stride;
        public long offset;
        public int vboId;
        public ByteBuffer clientPointer;
        public boolean genericPointer;

        public boolean isClientSide() {
            return enabled && clientPointer != null;
        }

        public void reset() {
            enabled = false;
            size = 0;
            type = 0;
            normalized = false;
            stride = 0;
            offset = 0;
            vboId = 0;
            clientPointer = null;
            genericPointer = false;
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
            return readComponent(type, normalized, buf, base, component);
        }

        public static float readComponent(int type, boolean normalized, ByteBuffer buf, int base, int component) {
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
