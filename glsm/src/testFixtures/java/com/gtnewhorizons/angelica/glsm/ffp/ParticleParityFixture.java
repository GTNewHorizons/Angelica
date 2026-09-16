package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.util.function.IntFunction;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;

public final class ParticleParityFixture {

    public record Particle(float cx, float cy, float cz, float half, float u0, float v0, float u1, float v1, int colorABGR) {}

    public record Rotation(float x, float xz, float z, float yz, float xy) {
        public static Rotation of(float yawDeg, float pitchDeg, boolean thirdPersonFront) {
            final float sign = thirdPersonFront ? -1f : 1f;
            final float yaw = (float) Math.toRadians(yawDeg);
            final float pitch = (float) Math.toRadians(pitchDeg);
            final float rotX = (float) Math.cos(yaw) * sign;
            final float rotZ = (float) Math.sin(yaw) * sign;
            final float rotYZ = -rotZ * (float) Math.sin(pitch) * sign;
            final float rotXY = rotX * (float) Math.sin(pitch) * sign;
            final float rotXZ = (float) Math.cos(pitch);
            return new Rotation(rotX, rotXZ, rotZ, rotYZ, rotXY);
        }

        public float offsetX(float a, float b) {
            return a * x + b * yz;
        }

        public float offsetY(float b) {
            return b * xz;
        }

        public float offsetZ(float a, float b) {
            return a * z + b * xy;
        }
    }

    private static final float[] CORNER_A = { -1.0f, -1.0f, 1.0f, 1.0f };
    private static final float[] CORNER_B = { -1.0f, 1.0f, 1.0f, -1.0f };

    private static final int REF_STRIDE = 24;

    private static int referenceVao;
    private static int referenceVbo;
    private static int instanceVbo;
    private static int checkerTexture;

    private ParticleParityFixture() {}

    public static float cornerA(int corner) {
        return CORNER_A[corner];
    }

    public static float cornerB(int corner) {
        return CORNER_B[corner];
    }

    public static Particle[] particles() {
        return new Particle[] {
            new Particle(-0.75f, 0.6f, 0f, 0.10f, 0f, 0f, 1f, 1f, 0xFF0000FF),
            new Particle(-0.45f, -0.55f, 0f, 0.16f, 0f, 0f, 1f, 1f, 0xFF00FF00),
            new Particle(-0.15f, 0.2f, 0f, 0.07f, 0f, 0f, 1f, 1f, 0xFFFF0000),
            new Particle(0.1f, 0.75f, 0f, -0.12f, 0f, 0f, 1f, 1f, 0xFF00FFFF),
            new Particle(0.35f, -0.3f, 0f, 0.20f, 0f, 0f, 1f, 1f, 0xFFFF00FF),
            new Particle(0.6f, 0.45f, 0f, -0.08f, 0f, 0f, 1f, 1f, 0xFFFFFF00),
            new Particle(0.8f, -0.7f, 0f, 0.13f, 0f, 0f, 1f, 1f, 0xFFFFFFFF),
            new Particle(-0.8f, -0.1f, 0f, 0.09f, 0f, 0f, 1f, 1f, 0xFF0080FF),
        };
    }

    private static int createCheckerTexture() {
        final int id = GLStateManager.glGenTextures();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        final ByteBuffer texels = BufferUtils.createByteBuffer(2 * 2 * 4);
        putTexel(texels, 255, 32, 32);
        putTexel(texels, 32, 255, 32);
        putTexel(texels, 32, 32, 255);
        putTexel(texels, 255, 255, 32);
        texels.flip();
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 2, 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        return id;
    }

    private static void putTexel(ByteBuffer buf, int r, int g, int b) {
        buf.put((byte) r).put((byte) g).put((byte) b).put((byte) 255);
    }

    public static void setupScene(boolean textureMatrix) {
        GLStateManager.glDisable(GL11.GL_FOG);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glDisable(GL11.GL_ALPHA_TEST);
        GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
        GLStateManager.glDisable(GL11.GL_CULL_FACE);
        GLStateManager.glDisable(GL11.GL_LIGHTING);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();

        checkerTexture = createCheckerTexture();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, checkerTexture);
        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        if (textureMatrix) {
            GLStateManager.glTranslatef(0.1f, 0.1f, 0.0f);
            GLStateManager.glScalef(0.8f, 0.8f, 1.0f);
        }
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private static ByteBuffer buildReferenceData(Particle[] particles, float rotX, float rotXZ, float rotZ, float rotYZ, float rotXY) {
        final ByteBuffer data = BufferUtils.createByteBuffer(particles.length * 4 * REF_STRIDE);
        for (Particle p : particles) {
            for (int i = 0; i < 4; i++) {
                final float a = cornerA(i);
                final float b = cornerB(i);
                final float ox = a * rotX + b * rotYZ;
                final float oy = b * rotXZ;
                final float oz = a * rotZ + b * rotXY;
                final float u = a < 0f ? p.u0() : p.u1();
                final float v = b < 0f ? p.v0() : p.v1();
                final int base = data.position();
                data.putFloat(base, p.cx() + ox * p.half());
                data.putFloat(base + 4, p.cy() + oy * p.half());
                data.putFloat(base + 8, p.cz() + oz * p.half());
                data.putInt(base + 12, p.colorABGR());
                data.putFloat(base + 16, u);
                data.putFloat(base + 20, v);
                data.position(base + REF_STRIDE);
            }
        }
        data.flip();
        return data;
    }

    public static void drawReference(Particle[] particles, float rotX, float rotXZ, float rotZ, float rotYZ, float rotXY) {
        final ByteBuffer data = buildReferenceData(particles, rotX, rotXZ, rotZ, rotYZ, rotXY);

        if (referenceVao == 0) referenceVao = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(referenceVao);
        if (referenceVbo == 0) referenceVbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, referenceVbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
        FfpFixture.attrib(0, 3, GL11.GL_FLOAT, false, REF_STRIDE, 0);
        FfpFixture.attrib(1, 4, GL11.GL_UNSIGNED_BYTE, true, REF_STRIDE, 12);
        FfpFixture.attrib(2, 2, GL11.GL_FLOAT, false, REF_STRIDE, 16);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        VAOManager.setCurrentVertexFlags(VertexFlags.COLOR_BIT | VertexFlags.TEXTURE_BIT);

        GLStateManager.glDrawArrays(GL11.GL_QUADS, 0, particles.length * 4);
        GLStateManager.glBindVertexArray(0);
    }

    public static void drawInstanced(Particle[] particles, float rotX, float rotXZ, float rotZ, float rotYZ, float rotXY) {
        ParticleQuadMesh.update(rotX, rotXZ, rotZ, rotYZ, rotXY);

        final ByteBuffer records = BufferUtils.createByteBuffer(particles.length * ParticleInstancedAttribs.STRIDE);
        final long records0 = memAddress0(records);
        for (int i = 0; i < particles.length; i++) {
            final Particle p = particles[i];
            final long ptr = records0 + (long) i * ParticleInstancedAttribs.STRIDE;
            ParticleInstancedAttribs.writeInstance(ptr, p.cx(), p.cy(), p.cz(), p.half(), p.u0(), p.v0(), p.u1(), p.v1(), p.colorABGR(), 0);
        }
        records.position(0).limit(particles.length * ParticleInstancedAttribs.STRIDE);

        GLStateManager.glBindVertexArray(ParticleQuadMesh.vao());
        VAOManager.setCurrentVertexFlags(ParticleQuadMesh.VERTEX_FLAGS);
        if (instanceVbo == 0) instanceVbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, records, GL15.GL_STREAM_DRAW);
        ParticleInstancedAttribs.pointInstanceAttribs(0L);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        GLStateManager.ffpInstancing = Instancing.PARTICLE;
        GLStateManager.glDrawArraysInstanced(GL11.GL_QUADS, 0, ParticleQuadMesh.VERTEX_COUNT, particles.length);
        GLStateManager.ffpInstancing = Instancing.NONE;
        GLStateManager.glBindVertexArray(0);
    }

    public static void assertPixelParity(int[] reference, int[] instanced, int size, int background, IntFunction<String> describe) {
        FfpFixture.assertPixelParity("instanced particles", reference, instanced, size, background, 16, 1, describe);
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
        if (checkerTexture != 0) {
            GLStateManager.glDeleteTextures(checkerTexture);
            checkerTexture = 0;
        }
        ParticleQuadMesh.delete();
    }
}
