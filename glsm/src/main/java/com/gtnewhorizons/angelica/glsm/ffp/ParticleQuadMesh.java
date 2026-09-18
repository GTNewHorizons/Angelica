package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.NormI8;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;

public final class ParticleQuadMesh {

    public static final int VERTEX_FLAGS = VertexFlags.TEXTURE_BIT;

    public static final int VERTEX_COUNT = 4;
    private static final int VERTEX_STRIDE = 24;

    private static final int OFFSET_OFFSET = 0;
    private static final int OFFSET_CORNER = 12;
    private static final int OFFSET_TANGENT = 20;

    private static final float[] CORNER_A = { -1.0f, -1.0f, 1.0f, 1.0f };
    private static final float[] CORNER_B = { -1.0f, 1.0f, 1.0f, -1.0f };

    private static int vao;
    private static int vbo;
    private static ByteBuffer staging;
    private static boolean written;
    private static float lastRotX, lastRotXZ, lastRotZ, lastRotYZ, lastRotXY;

    private ParticleQuadMesh() {}

    public static float cornerA(int corner) {
        return CORNER_A[corner];
    }

    public static float cornerB(int corner) {
        return CORNER_B[corner];
    }

    public static float offsetX(float a, float b, float rotX, float rotYZ) {
        return a * rotX + b * rotYZ;
    }

    public static float offsetY(float b, float rotXZ) {
        return b * rotXZ;
    }

    public static float offsetZ(float a, float b, float rotZ, float rotXY) {
        return a * rotZ + b * rotXY;
    }

    public static int vao() {
        if (vao == 0) build();
        return vao;
    }

    public static void update(float rotX, float rotXZ, float rotZ, float rotYZ, float rotXY) {
        if (vao == 0) build();
        if (written && rotX == lastRotX && rotXZ == lastRotXZ && rotZ == lastRotZ && rotYZ == lastRotYZ && rotXY == lastRotXY) {
            return;
        }
        lastRotX = rotX;
        lastRotXZ = rotXZ;
        lastRotZ = rotZ;
        lastRotYZ = rotYZ;
        lastRotXY = rotXY;
        written = true;

        write(memAddress0(staging), rotX, rotXZ, rotZ, rotYZ, rotXY);
        final int savedVBO = GLStateManager.getBoundVBO();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        staging.position(0).limit(VERTEX_COUNT * VERTEX_STRIDE);
        GLStateManager.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, staging);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedVBO);
    }

    private static void write(long dst, float rotX, float rotXZ, float rotZ, float rotYZ, float rotXY) {
        final int tangent = packTangent(rotX, 0.0f, rotZ);
        long ptr = dst;
        for (int i = 0; i < VERTEX_COUNT; i++) {
            final float a = cornerA(i);
            final float b = cornerB(i);
            memPutFloat(ptr + OFFSET_OFFSET, offsetX(a, b, rotX, rotYZ));
            memPutFloat(ptr + OFFSET_OFFSET + 4, offsetY(b, rotXZ));
            memPutFloat(ptr + OFFSET_OFFSET + 8, offsetZ(a, b, rotZ, rotXY));
            memPutFloat(ptr + OFFSET_CORNER, (a + 1.0f) * 0.5f);
            memPutFloat(ptr + OFFSET_CORNER + 4, (b + 1.0f) * 0.5f);
            memPutInt(ptr + OFFSET_TANGENT, tangent);
            ptr += VERTEX_STRIDE;
        }
    }

    private static int packTangent(float x, float y, float z) {
        final float lenSq = x * x + y * y + z * z;
        final float nx, ny, nz;
        if (lenSq < 1.0e-12f) {
            nx = 1.0f;
            ny = 0.0f;
            nz = 0.0f;
        } else {
            final float inv = (float) (1.0 / Math.sqrt(lenSq));
            nx = x * inv;
            ny = y * inv;
            nz = z * inv;
        }
        return NormI8.pack(nx, ny, nz) | (127 << 24);
    }

    private static void build() {
        final int savedVAO = GLStateManager.getBoundVAO();
        final int savedVBO = GLStateManager.getBoundVBO();
        vao = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vao);
        vbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, (long) VERTEX_COUNT * VERTEX_STRIDE, GL15.GL_DYNAMIC_DRAW);

        attrib(VertexFormatElement.Usage.POSITION.getAttributeLocation(), 3, GL11.GL_FLOAT, false, OFFSET_OFFSET);
        attrib(VertexFormatElement.Usage.PRIMARY_UV.getAttributeLocation(), 2, GL11.GL_FLOAT, false, OFFSET_CORNER);
        attrib(ImmediateExtendedAttribHandler.LOC_TANGENT, 4, GL11.GL_BYTE, true, OFFSET_TANGENT);

        ParticleInstancedAttribs.enableInstanceArrays();

        GLStateManager.glBindVertexArray(savedVAO);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedVBO);

        if (staging == null) staging = memAlloc(VERTEX_COUNT * VERTEX_STRIDE);
        written = false;
    }

    private static void attrib(int loc, int size, int type, boolean normalized, int offset) {
        GLStateManager.glEnableVertexAttribArray(loc);
        GLStateManager.glVertexAttribPointer(loc, size, type, normalized, VERTEX_STRIDE, offset);
    }

    public static void delete() {
        if (vbo != 0) {
            GLStateManager.glDeleteBuffers(vbo);
            vbo = 0;
        }
        if (vao != 0) {
            GLStateManager.glDeleteVertexArrays(vao);
            vao = 0;
        }
        if (staging != null) {
            memFree(staging);
            staging = null;
        }
        written = false;
    }
}
