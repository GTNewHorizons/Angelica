package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.function.IntFunction;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;

public final class CubeParityFixture {

    private static final int TEX_WIDTH = 64;
    private static final int TEX_HEIGHT = 32;
    private static final float SCALE = 0.0625f;
    private static final short BRIGHTNESS = 240;

    public record Spec(int texU, int texV, float x, float y, float z, int sizeX, int sizeY, int sizeZ, float inflate, boolean mirror) {}

    private static final float[] MODELVIEW = {
        1.25f, 0.0f, 0.0f, 0.0f,
        0.0f, 1.25f, 0.0f, 0.0f,
        0.375f, 0.25f, -0.25f, 0.0f,
        0.0f, 0.0f, 0.0f, 1.0f,
    };

    private static final float[] MODELVIEW_CONFORMAL = {
        1.0f, 0.0f, 0.75f, 0.0f,
        0.0f, 1.25f, 0.0f, 0.0f,
        -0.75f, 0.0f, 1.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 1.0f,
    };

    private static float[] modelView = MODELVIEW;

    private static int netTexture;
    private static int lightmapTexture;

    private CubeParityFixture() {}

    public static Spec[] specs() {
        return new Spec[] {
            new Spec(0, 0, -4, -6, -2, 8, 12, 4, 0.0f, false),
            new Spec(16, 8, -3, 1, -3, 6, 4, 6, 0.25f, true),
        };
    }

    private static void setupCamera(boolean conformal) {
        modelView = conformal ? MODELVIEW_CONFORMAL : MODELVIEW;
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        final FloatBuffer mv = BufferUtils.createFloatBuffer(16);
        mv.put(modelView).flip();
        GLStateManager.glLoadMatrix(mv);
    }

    public static void setupScene(boolean lightmap, boolean lit) {
        GLStateManager.glDisable(GL11.GL_FOG);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glDisable(GL11.GL_ALPHA_TEST);
        GLStateManager.glEnable(GL11.GL_DEPTH_TEST);
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);
        GLStateManager.glEnable(GL11.GL_CULL_FACE);
        GLStateManager.glCullFace(GL11.GL_BACK);
        setupCamera(lit);
        if (lit) {
            enableDirectionalLight();
        } else {
            GLStateManager.glDisable(GL11.GL_LIGHTING);
        }

        netTexture = createNetTexture();
        if (lightmap) {
            lightmapTexture = createNetTexture();
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, lightmapTexture);
            GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
            GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
            GLStateManager.glLoadIdentity();
            GLStateManager.glScalef(0.00390625f, 0.00390625f, 0.00390625f);
            GLStateManager.glTranslatef(8.0f, 8.0f, 8.0f);
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, netTexture);
        }
    }

    private static void enableDirectionalLight() {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(4);
        GLStateManager.glEnable(GL11.GL_LIGHTING);
        GLStateManager.glEnable(GL11.GL_NORMALIZE);
        GLStateManager.glEnable(GL11.GL_LIGHT0);
        GLStateManager.glDisable(GL11.GL_LIGHT1);
        GLStateManager.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, fill(buf, 0.2f, 0.2f, 0.2f, 1.0f));
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, fill(buf, 0.375f, 0.75f, 0.5f, 0.0f));
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, fill(buf, 1.0f, 1.0f, 1.0f, 1.0f));
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_AMBIENT, fill(buf, 0.0f, 0.0f, 0.0f, 1.0f));
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_SPECULAR, fill(buf, 0.0f, 0.0f, 0.0f, 1.0f));
        GLStateManager.glEnable(GL11.GL_COLOR_MATERIAL);
        GLStateManager.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
    }

    public static void disableDirectionalLight() {
        GLStateManager.glDisable(GL11.GL_COLOR_MATERIAL);
        GLStateManager.glDisable(GL11.GL_LIGHT0);
        GLStateManager.glDisable(GL11.GL_NORMALIZE);
        GLStateManager.glDisable(GL11.GL_LIGHTING);
    }

    private static FloatBuffer fill(FloatBuffer buf, float x, float y, float z, float w) {
        buf.clear();
        buf.put(x).put(y).put(z).put(w).flip();
        return buf;
    }

    private static int createNetTexture() {
        final int id = GLStateManager.glGenTextures();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        final ByteBuffer texels = BufferUtils.createByteBuffer(TEX_WIDTH * TEX_HEIGHT * 4);
        for (int y = 0; y < TEX_HEIGHT; y++) {
            for (int x = 0; x < TEX_WIDTH; x++) {
                texels.put((byte) (16 + x * 3));
                texels.put((byte) (16 + y * 7));
                texels.put((byte) (255 - x * 3));
                texels.put((byte) 255);
            }
        }
        texels.flip();
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, TEX_WIDTH, TEX_HEIGHT, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        return id;
    }

    private static final int REF_STRIDE = 44;

    private static int referenceVao;
    private static int referenceVbo;

    private static ByteBuffer buildQuadData(Spec[] specs) {
        final ByteBuffer data = BufferUtils.createByteBuffer(specs.length * 24 * REF_STRIDE);
        for (Spec spec : specs) {
            final float[] corners = corners(spec);
            for (int face = 0; face < UnitCubeMesh.FACE_COUNT; face++) {
                final float[] uv = faceRect(spec, face);
                final float[] normal = UnitCubeMesh.FACES[face].normal();
                for (int corner = 0; corner < 4; corner++) {
                    final int source = spec.mirror() ? 3 - corner : corner;
                    final int index = UnitCubeMesh.FACES[face].corners()[source];
                    final int base = data.position();
                    data.putFloat(base, corners[index * 3] * SCALE);
                    data.putFloat(base + 4, corners[index * 3 + 1] * SCALE);
                    data.putFloat(base + 8, corners[index * 3 + 2] * SCALE);
                    data.putInt(base + 12, 0xFFFFFFFF);
                    data.putFloat(base + 16, uv[(source == 0 || source == 3) ? 2 : 0]);
                    data.putFloat(base + 20, uv[source >= 2 ? 3 : 1]);
                    data.putFloat(base + 24, BRIGHTNESS);
                    data.putFloat(base + 28, BRIGHTNESS);
                    data.putFloat(base + 32, spec.mirror() ? -normal[0] : normal[0]);
                    data.putFloat(base + 36, normal[1]);
                    data.putFloat(base + 40, normal[2]);
                    data.position(base + REF_STRIDE);
                }
            }
        }
        data.flip();
        return data;
    }

    private static int uploadQuads(int vbo, Spec[] specs, boolean lightmap) {
        final ByteBuffer data = buildQuadData(specs);
        if (vbo == 0) vbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
        FfpFixture.attrib(0, 3, GL11.GL_FLOAT, false, REF_STRIDE, 0);
        FfpFixture.attrib(1, 4, GL11.GL_UNSIGNED_BYTE, true, REF_STRIDE, 12);
        FfpFixture.attrib(2, 2, GL11.GL_FLOAT, false, REF_STRIDE, 16);
        if (lightmap) {
            FfpFixture.attrib(3, 2, GL11.GL_FLOAT, false, REF_STRIDE, 24);
        } else {
            GLStateManager.glDisableVertexAttribArray(3);
        }
        FfpFixture.attrib(4, 3, GL11.GL_FLOAT, false, REF_STRIDE, 32);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return vbo;
    }

    public static void drawReference(Spec[] specs, boolean lightmap) {
        if (referenceVao == 0) referenceVao = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(referenceVao);
        referenceVbo = uploadQuads(referenceVbo, specs, lightmap);
        VAOManager.setCurrentVertexFlags(UnitCubeMesh.VERTEX_FLAGS | (lightmap ? VertexFlags.BRIGHTNESS_BIT : 0));

        GLStateManager.glDrawArrays(GL11.GL_QUADS, 0, specs.length * 24);
        GLStateManager.glBindVertexArray(0);
    }

    private static float[] corners(Spec spec) {
        final float minX = spec.x() - spec.inflate();
        final float minY = spec.y() - spec.inflate();
        final float minZ = spec.z() - spec.inflate();
        final float maxX = (spec.x() + spec.sizeX()) + spec.inflate();
        final float maxY = (spec.y() + spec.sizeY()) + spec.inflate();
        final float maxZ = (spec.z() + spec.sizeZ()) + spec.inflate();
        final float lowX = spec.mirror() ? maxX : minX;
        final float highX = spec.mirror() ? minX : maxX;
        final float[] out = new float[UnitCubeMesh.CORNERS.length * 3];
        for (int index = 0; index < UnitCubeMesh.CORNERS.length; index++) {
            final float[] corner = UnitCubeMesh.CORNERS[index];
            out[index * 3] = corner[0] > 0f ? highX : lowX;
            out[index * 3 + 1] = corner[1] > 0f ? maxY : minY;
            out[index * 3 + 2] = corner[2] > 0f ? maxZ : minZ;
        }
        return out;
    }

    private static float[] faceRect(Spec spec, int face) {
        final int i = spec.texU();
        final int j = spec.texV();
        final int k = spec.sizeX();
        final int l = spec.sizeY();
        final int m = spec.sizeZ();
        final int[] rect = switch (face) {
            case 0 -> new int[] { i + m + k, j + m, i + m + k + m, j + m + l };
            case 1 -> new int[] { i, j + m, i + m, j + m + l };
            case 2 -> new int[] { i + m, j, i + m + k, j + m };
            case 3 -> new int[] { i + m + k, j + m, i + m + k + k, j };
            case 4 -> new int[] { i + m, j + m, i + m + k, j + m + l };
            default -> new int[] { i + m + k + m, j + m, i + m + k + m + k, j + m + l };
        };
        return new float[] {
            rect[0] / (float) TEX_WIDTH, rect[1] / (float) TEX_HEIGHT,
            rect[2] / (float) TEX_WIDTH, rect[3] / (float) TEX_HEIGHT,
        };
    }

    private static int instanceVbo;

    public static void drawInstanced(Spec[] specs) {
        final ByteBuffer records = BufferUtils.createByteBuffer(specs.length * CubeInstancedAttribs.STRIDE);
        final long records0 = memAddress0(records);
        for (int index = 0; index < specs.length; index++) {
            final Spec spec = specs[index];
            final CubeParams cube = CubeParams.of(TEX_WIDTH, TEX_HEIGHT, spec.mirror(), spec.texU(), spec.texV(), spec.x(), spec.y(), spec.z(), spec.sizeX(), spec.sizeY(), spec.sizeZ(), spec.inflate());
            if (cube == null) {
                throw new AssertionError("spec " + index + " is not cube instanceable");
            }
            final long ptr = records0 + (long) index * CubeInstancedAttribs.STRIDE;
            cube.writeRows(ptr, modelView, 0, SCALE);
            InstancedAttribs.writeTail(ptr, 0xFFFFFFFF, 0, 0L);
            memPutFloat(ptr + CubeInstancedAttribs.OFFSET_LIGHTMAP_SCALE, BRIGHTNESS);
            memPutFloat(ptr + CubeInstancedAttribs.OFFSET_LIGHTMAP_SCALE + 4, BRIGHTNESS);
            cube.writeTexture(ptr);
        }
        records.position(0).limit(specs.length * CubeInstancedAttribs.STRIDE);

        GLStateManager.glBindVertexArray(UnitCubeMesh.vao());
        VAOManager.setCurrentVertexFlags(UnitCubeMesh.VERTEX_FLAGS);
        if (instanceVbo == 0) instanceVbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, records, GL15.GL_STREAM_DRAW);
        CubeInstancedAttribs.pointInstanceAttribs(0L);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        GLStateManager.ffpInstancing = Instancing.CUBE;
        GLStateManager.glDrawArraysInstanced(GL11.GL_QUADS, 0, UnitCubeMesh.VERTEX_COUNT, specs.length);
        GLStateManager.ffpInstancing = Instancing.NONE;
        GLStateManager.glBindVertexArray(0);
    }

    public static void assertPixelParity(int[] reference, int[] instanced, int size, int background, IntFunction<String> describe) {
        FfpFixture.assertPixelParity("instanced cubes", reference, instanced, size, background, 8, 1, describe);
    }

    public static void deleteResources() {
        if (instanceVbo != 0) {
            GLStateManager.glDeleteBuffers(instanceVbo);
            instanceVbo = 0;
        }
        if (referenceVbo != 0) {
            GLStateManager.glDeleteBuffers(referenceVbo);
            referenceVbo = 0;
        }
        if (referenceVao != 0) {
            GLStateManager.glDeleteVertexArrays(referenceVao);
            referenceVao = 0;
        }
        if (netTexture != 0) {
            GLStateManager.glDeleteTextures(netTexture);
            netTexture = 0;
        }
        if (lightmapTexture != 0) {
            GLStateManager.glDeleteTextures(lightmapTexture);
            lightmapTexture = 0;
        }
        UnitCubeMesh.delete();
    }
}
