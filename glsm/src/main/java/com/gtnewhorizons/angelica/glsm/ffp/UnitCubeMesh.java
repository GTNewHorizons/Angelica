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

public final class UnitCubeMesh {

    public static final int VERTEX_FLAGS = VertexFlags.COLOR_BIT | VertexFlags.TEXTURE_BIT | VertexFlags.NORMAL_BIT;

    public static final int FACE_COUNT = 6;
    public static final int VERTEX_COUNT = 24;
    public static final int VERTEX_STRIDE = 64;

    public static final int OFFSET_POSITION = 0;
    private static final int OFFSET_COLOR = 12;
    public static final int OFFSET_MID = 16;
    public static final int OFFSET_DELTA = 32;
    public static final int OFFSET_NORMAL = 48;
    public static final int OFFSET_TANGENT = 60;

    public static final float[][] CORNERS = {
        { 0, 0, 0 }, { 1, 0, 0 }, { 1, 1, 0 }, { 0, 1, 0 },
        { 0, 0, 1 }, { 1, 0, 1 }, { 1, 1, 1 }, { 0, 1, 1 },
    };

    public record Span(int a, int z) {
        public int texels(int sizeA, int sizeZ) {
            return a * sizeA + z * sizeZ;
        }
    }

    public record Net(Span uLow, Span uHigh, Span vLow, Span vHigh) {
        public Span u(int vertex) {
            return vertex == 0 || vertex == 3 ? uHigh : uLow;
        }

        public Span v(int vertex) {
            return vertex >= 2 ? vHigh : vLow;
        }
    }

    public record Face(int[] corners, float[] normal, float[] tangent, Net net) {}

    public static final Face[] FACES = {
        new Face(new int[] { 5, 1, 2, 6 }, new float[] { 1, 0, 0 }, new float[] { 0, 0, 1, 1 }, new Net(new Span(1, 1), new Span(1, 2), new Span(0, 1), new Span(1, 1))),
        new Face(new int[] { 0, 4, 7, 3 }, new float[] { -1, 0, 0 }, new float[] { 0, 0, -1, 1 }, new Net(new Span(0, 0), new Span(0, 1), new Span(0, 1), new Span(1, 1))),
        new Face(new int[] { 5, 4, 0, 1 }, new float[] { 0, -1, 0 }, new float[] { 1, 0, 0, 1 }, new Net(new Span(0, 1), new Span(1, 1), new Span(0, 0), new Span(0, 1))),
        new Face(new int[] { 2, 3, 7, 6 }, new float[] { 0, 1, 0 }, new float[] { 1, 0, 0, -1 }, new Net(new Span(1, 1), new Span(2, 1), new Span(0, 1), new Span(0, 0))),
        new Face(new int[] { 1, 0, 3, 2 }, new float[] { 0, 0, -1 }, new float[] { 1, 0, 0, 1 }, new Net(new Span(0, 1), new Span(1, 1), new Span(0, 1), new Span(1, 1))),
        new Face(new int[] { 4, 5, 6, 7 }, new float[] { 0, 0, 1 }, new float[] { -1, 0, 0, 1 }, new Net(new Span(1, 2), new Span(2, 2), new Span(0, 1), new Span(1, 1))),
    };

    private static int vao;
    private static int vbo;

    private UnitCubeMesh() {}

    public static void write(long dst) {
        long ptr = dst;
        for (int f = 0; f < FACE_COUNT; f++) {
            final Face face = FACES[f];
            final Net net = face.net();
            final float midUA = (net.uLow().a() + net.uHigh().a()) * 0.5f;
            final float midUZ = (net.uLow().z() + net.uHigh().z()) * 0.5f;
            final float midVA = (net.vLow().a() + net.vHigh().a()) * 0.5f;
            final float midVZ = (net.vLow().z() + net.vHigh().z()) * 0.5f;
            for (int c = 0; c < 4; c++) {
                final float[] corner = CORNERS[face.corners()[c]];
                final Span u = net.u(c);
                final Span v = net.v(c);
                memPutFloat(ptr + OFFSET_POSITION, corner[0]);
                memPutFloat(ptr + OFFSET_POSITION + 4, corner[1]);
                memPutFloat(ptr + OFFSET_POSITION + 8, corner[2]);
                memPutInt(ptr + OFFSET_COLOR, 0xFFFFFFFF);
                memPutFloat(ptr + OFFSET_MID, midUA);
                memPutFloat(ptr + OFFSET_MID + 4, midUZ);
                memPutFloat(ptr + OFFSET_MID + 8, midVA);
                memPutFloat(ptr + OFFSET_MID + 12, midVZ);
                memPutFloat(ptr + OFFSET_DELTA, u.a() - midUA);
                memPutFloat(ptr + OFFSET_DELTA + 4, u.z() - midUZ);
                memPutFloat(ptr + OFFSET_DELTA + 8, v.a() - midVA);
                memPutFloat(ptr + OFFSET_DELTA + 12, v.z() - midVZ);
                memPutFloat(ptr + OFFSET_NORMAL, face.normal()[0]);
                memPutFloat(ptr + OFFSET_NORMAL + 4, face.normal()[1]);
                memPutFloat(ptr + OFFSET_NORMAL + 8, face.normal()[2]);
                final float[] tangent = face.tangent();
                memPutInt(ptr + OFFSET_TANGENT, packTangent(tangent[0], tangent[1], tangent[2], tangent[3]));
                ptr += VERTEX_STRIDE;
            }
        }
    }

    private static int packTangent(float x, float y, float z, float w) {
        return NormI8.pack(x, y, z) | (((int) (w * 127.0f) & 0xFF) << 24);
    }

    public static int vao() {
        if (vao == 0) build();
        return vao;
    }

    private static void build() {
        final int savedVAO = GLStateManager.getBoundVAO();
        final int savedVBO = GLStateManager.getBoundVBO();
        vao = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vao);
        vbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        final int bytes = VERTEX_COUNT * VERTEX_STRIDE;
        final ByteBuffer data = memAlloc(bytes);
        try {
            write(memAddress0(data));
            data.position(0).limit(bytes);
            GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
        } finally {
            memFree(data);
        }

        attrib(VertexFormatElement.Usage.POSITION.getAttributeLocation(), 3, GL11.GL_FLOAT, false, OFFSET_POSITION);
        attrib(VertexFormatElement.Usage.COLOR.getAttributeLocation(), 4, GL11.GL_UNSIGNED_BYTE, true, OFFSET_COLOR);
        attrib(VertexFormatElement.Usage.PRIMARY_UV.getAttributeLocation(), 4, GL11.GL_FLOAT, false, OFFSET_MID);
        attrib(VertexFormatElement.Usage.SECONDARY_UV.getAttributeLocation(), 4, GL11.GL_FLOAT, false, OFFSET_DELTA);
        attrib(VertexFormatElement.Usage.NORMAL.getAttributeLocation(), 3, GL11.GL_FLOAT, false, OFFSET_NORMAL);
        attrib(ImmediateExtendedAttribHandler.LOC_TANGENT, 4, GL11.GL_BYTE, true, OFFSET_TANGENT);

        CubeInstancedAttribs.enableInstanceArrays();

        GLStateManager.glBindVertexArray(savedVAO);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedVBO);
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
    }
}
