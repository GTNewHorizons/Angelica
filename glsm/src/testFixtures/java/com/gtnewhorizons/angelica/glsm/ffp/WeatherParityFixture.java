package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.util.function.IntFunction;

public final class WeatherParityFixture {

    public record Spec(float x, float z, float bottom, float top, float uBase, float uDrift, float vBase, float vDrift,
        int brightness, int scrollOffset, float scrollScale, float source,
        float r, float g, float b, float a, int period, float uDriftScale, float vDriftScale, float fadeScale) {}

    private static final int TICK = 2650;
    private static final float PARTIAL_TICKS = 0.375f;
    private static final float TRANSLATE_X = 0.37f;
    private static final float TRANSLATE_Y = -62.25f;
    private static final float TRANSLATE_Z = -0.61f;
    private static final float INV_RADIUS = 1.0f / 6.0f;
    private static final float CAMERA_FRAC_X = 0.3f;
    private static final float CAMERA_FRAC_Z = 0.7f;
    private static final float STRENGTH = 0.8f;
    private static final int PROVIDER_TICK = 1777;
    private static final float PROVIDER_INV_RADIUS = 1.0f / 9.0f;

    private static final float[] CORNER_X = { 0.0f, 1.0f, 1.0f, 0.0f };
    private static final float[] CORNER_Y = { 0.0f, 0.0f, 1.0f, 1.0f };

    private static final int REF_STRIDE = 44;

    private static int referenceVao;
    private static int referenceVbo;
    private static int instanceVbo;
    private static int weatherTexture;
    private static int lightmapTexture;

    private WeatherParityFixture() {}

    private static Spec rain(float x, float z, float bottom, float top, float j0, int hash, int brightness) {
        return new Spec(x, z, bottom, top, 0f, 0f, 0f, 0f, brightness, hash, WeatherInstancedAttribs.RAIN_SCROLL_BASE + j0, WeatherInstancedAttribs.SOURCE_VANILLA,
            1f, 1f, 1f, 1f, WeatherInstancedAttribs.RAIN_PERIOD, 0f, 0f, WeatherInstancedAttribs.RAIN_FADE);
    }

    private static Spec snow(float x, float z, float bottom, float top, float j0, float j1, float j2, float j3, int brightness) {
        return new Spec(x, z, bottom, top, j0, j1, j2, j3, brightness, 0, 1f, WeatherInstancedAttribs.SOURCE_VANILLA,
            1f, 1f, 1f, 1f, WeatherInstancedAttribs.SNOW_PERIOD, WeatherInstancedAttribs.SNOW_U_DRIFT, WeatherInstancedAttribs.SNOW_V_DRIFT,
            WeatherInstancedAttribs.SNOW_FADE);
    }

    private static Spec provider(float x, float z, float bottom, float top, float uBase, float uDrift, float vBase, float vDrift,
        int brightness, int scrollOffset, float scrollScale, float r, float g, float b, float a,
        int period, float uDriftScale, float vDriftScale, float fadeScale) {
        return new Spec(x, z, bottom, top, uBase, uDrift, vBase, vDrift, brightness, scrollOffset, scrollScale, WeatherInstancedAttribs.SOURCE_PROVIDER, r, g, b, a,
            period, uDriftScale, vDriftScale, fadeScale);
    }

    public static Spec[] specs() {
        return new Spec[] {
            rain(-4f, -3f, 58f, 66f, 0.31f, 5, 0x00F00030),
            rain(2f, -4f, 60f, 67f, 0.77f, 19, 0x00A000D0),
            rain(-2f, 3f, 57f, 65f, 0.05f, 62, 0x0060F0A0),
            rain(4f, 2f, 59f, 64f, 0.58f, 11, 0x00D01090),
            rain(1f, 4f, 56f, 66f, 0.93f, 24, 0x0080C0F0),
            snow(-5f, 1f, 58f, 66f, 0.12f, 0.47f, 0.83f, 0.29f, 0x00E02070),
            snow(3f, -2f, 60f, 68f, 0.66f, 0.91f, 0.18f, 0.74f, 0x00B0B0B0),
            snow(-1f, -5f, 57f, 63f, 0.39f, 0.25f, 0.52f, 0.96f, 0x0040E0C0),
            snow(5f, 4f, 59f, 67f, 0.84f, 0.63f, 0.07f, 0.41f, 0x00F0F0F0),
            snow(-3f, 5f, 55f, 65f, 0.22f, 0.78f, 0.35f, 0.58f, 0x00109050),
            provider(-5f, -4f, 58f, 66f, 0.14f, 0.52f, 0.71f, 0.33f, 0x00C06040, 0, 1f, 1f, 1f, 1f, 1f, 512, 0.03f, 0.001f, 0.3f),
            provider(4f, -3f, 59f, 67f, 0.61f, 0.27f, 0.09f, 0.88f, 0x00F020B0, 0, 1f, 1f, 1f, 1f, 1f, 512, 0.03f, 0.001f, 0.3f),
            provider(-4f, 2f, 57f, 65f, 0.42f, 0.36f, 0.64f, 0.19f, 0x0090D070, 0, 0f, 0.12f, 0.27f, 0.18f, 2.0f, 512, 0.03f, 0.003f, 0f),
            provider(2f, 5f, 60f, 66f, 0.83f, 0.74f, 0.25f, 0.57f, 0x0050A0E0, 0, 0f, 0.29f, 0.14f, 0.21f, 2.0f, 512, 0.03f, 0.003f, 0f),
            provider(-2f, -5f, 56f, 64f, 0.07f, 0.45f, 0.93f, 0.62f, 0x00B0F040, 0, -1f, 0.83f, 0.92f, 0.88f, 1f, 1024, 0.001f, 0.001f, 0.3f),
            provider(5f, 1f, 58f, 68f, 0.55f, 0.16f, 0.38f, 0.81f, 0x007070F0, 0, -1f, 0.97f, 0.86f, 0.81f, 1f, 1024, 0.001f, 0.001f, 0.3f),
            provider(-5f, 4f, 59f, 66f, 0f, 0.68f, 0f, 0.23f, 0x00E0E050, 0, -1f, 1f, 1f, 1f, 0.35f, 512, 0f, 0.01f, 0.3f),
            provider(3f, 3f, 57f, 67f, 0f, 0.49f, 0f, 0.91f, 0x0030C0A0, 0, -1f, 1f, 1f, 1f, 0.82f, 512, 0f, 0.01f, 0.3f),
            provider(0f, -4f, 58f, 65f, 0.26f, 0.12f, 0.67f, 0.44f, 0x00A0A0A0, 21, 3.46f, 1f, 1f, 1f, 1f, 32, 0f, 0f, 0.3f),
            provider(1f, 3f, 60f, 66f, 0.89f, 0.31f, 0.15f, 0.76f, 0x00F0A040, 7, 3.81f, 1f, 1f, 1f, 1f, 32, 0f, 0f, 0.3f),
            provider(-3f, 0f, 56f, 67f, 0.33f, 0.85f, 0.48f, 0.13f, 0x00D0D0D0, 0, 1f, 1f, 1f, 1f, 0.24f, 512, 0.01f, 0.01f, 0.3f),
            provider(0f, 5f, 58f, 64f, 0.72f, 0.04f, 0.59f, 0.97f, 0x0010F0F0, 0, 1f, 1f, 1f, 1f, 0.67f, 512, 0.01f, 0.01f, 0.3f),
        };
    }

    private static float mod(float x, float y) {
        return x - y * (float) Math.floor(x / y);
    }

    public static void setupScene() {
        GLSMEnable.glDisable(GL11.GL_FOG);
        GLSMEnable.glDisable(GL11.GL_ALPHA_TEST);
        GLSMEnable.glDisable(GL11.GL_DEPTH_TEST);
        GLSMEnable.glDisable(GL11.GL_CULL_FACE);
        GLSMEnable.glDisable(GL11.GL_LIGHTING);
        GLSMEnable.glEnable(GL11.GL_BLEND);
        GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glOrtho(-8.0, 8.0, -8.0, 8.0, -64.0, 64.0);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glRotatef(30.0f, 1.0f, 0.0f, 0.0f);
        GLSMMatrices.glRotatef(35.0f, 0.0f, 1.0f, 0.0f);

        lightmapTexture = createTexture(GL13.GL_TEXTURE1, 16, GL11.GL_LINEAR, GL11.GL_CLAMP);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glScalef(0.00390625f, 0.00390625f, 0.00390625f);
        GLSMMatrices.glTranslatef(8.0f, 8.0f, 8.0f);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);

        weatherTexture = createTexture(GL13.GL_TEXTURE0, 4, GL11.GL_NEAREST, GL11.GL_REPEAT);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private static int createTexture(int unit, int size, int filter, int wrap) {
        final int id = GLSMTextures.glGenTextures();
        GLSMTextures.glActiveTexture(unit);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrap);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrap);
        final ByteBuffer texels = BufferUtils.createByteBuffer(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                texels.put((byte) (40 + x * (200 / size)));
                texels.put((byte) (40 + y * (200 / size)));
                texels.put((byte) (255 - (x + y) * (100 / size)));
                texels.put((byte) 255);
            }
        }
        texels.flip();
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
        GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
        return id;
    }

    private static void putVertex(ByteBuffer data, float px, float py, float pz, float r, float g, float b, float a,
        float u, float v, float lmX, float lmY) {
        final int base = data.position();
        data.putFloat(base, px);
        data.putFloat(base + 4, py);
        data.putFloat(base + 8, pz);
        data.putFloat(base + 12, r);
        data.putFloat(base + 16, g);
        data.putFloat(base + 20, b);
        data.putFloat(base + 24, a);
        data.putFloat(base + 28, u);
        data.putFloat(base + 32, v);
        data.putFloat(base + 36, lmX);
        data.putFloat(base + 40, lmY);
        data.position(base + REF_STRIDE);
    }

    private static float tessellatorByte(float channel) {
        final int value = (int) (channel * 255.0F);
        return Math.max(0, Math.min(255, value)) / 255.0f;
    }

    private static void addColumn(ByteBuffer data, Spec s) {
        final boolean provider = s.source() == WeatherInstancedAttribs.SOURCE_PROVIDER;
        final int tick = provider ? PROVIDER_TICK : TICK;
        final float age = tick + PARTIAL_TICKS;
        final float invRadius = provider ? PROVIDER_INV_RADIUS : INV_RADIUS;
        final float strength = provider ? 1.0f : STRENGTH;

        final float halfScale = 0.5f / (float) Math.sqrt(s.x() * s.x() + s.z() * s.z());
        final float halfX = -s.z() * halfScale;
        final float halfZ = s.x() * halfScale;

        final float dx = s.x() + 0.5f - CAMERA_FRAC_X;
        final float dz = s.z() + 0.5f - CAMERA_FRAC_Z;
        final float distance = (float) Math.sqrt(dx * dx + dz * dz) * invRadius;
        final float fade = 1.0f - distance * distance;

        final float scroll = (mod((float) tick + s.scrollOffset(), s.period()) + PARTIAL_TICKS) / s.period() * s.scrollScale();
        final float driftU = s.uBase() + age * s.uDriftScale() * s.uDrift();
        final float driftV = s.vBase() + age * s.vDriftScale() * s.vDrift();
        final float alpha = fade * s.fadeScale() + 0.5f;

        final float r = tessellatorByte(s.r());
        final float g = tessellatorByte(s.g());
        final float b = tessellatorByte(s.b());
        final float a = tessellatorByte(alpha * s.a() * strength);
        final float lmX = s.brightness() & 0xFFFF;
        final float lmY = s.brightness() >> 16 & 0xFFFF;

        for (int i = 0; i < 4; i++) {
            final float side = CORNER_X[i] * 2.0f - 1.0f;
            final float cornerX = s.x() + 0.5f + side * halfX;
            final float cornerZ = s.z() + 0.5f + side * halfZ;
            final float cornerY = s.bottom() * (1.0f - CORNER_Y[i]) + s.top() * CORNER_Y[i];
            putVertex(data, cornerX + TRANSLATE_X, cornerY + TRANSLATE_Y, cornerZ + TRANSLATE_Z, r, g, b, a,
                CORNER_X[i] + driftU, cornerY * 0.25f + scroll + driftV, lmX, lmY);
        }
    }

    private static ByteBuffer buildReferenceData(Spec[] specs) {
        final ByteBuffer data = BufferUtils.createByteBuffer(specs.length * 4 * REF_STRIDE);
        for (Spec s : specs) {
            addColumn(data, s);
        }
        data.flip();
        return data;
    }

    public static void drawReference(Spec[] specs) {
        final ByteBuffer data = buildReferenceData(specs);
        if (referenceVao == 0) referenceVao = GLSMVertexInput.glGenVertexArrays();
        GLSMVertexInput.glBindVertexArray(referenceVao);
        if (referenceVbo == 0) referenceVbo = GLSMBuffers.glGenBuffers();
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, referenceVbo);
        GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
        FfpFixture.attrib(0, 3, GL11.GL_FLOAT, false, REF_STRIDE, 0);
        FfpFixture.attrib(1, 4, GL11.GL_FLOAT, false, REF_STRIDE, 12);
        FfpFixture.attrib(2, 2, GL11.GL_FLOAT, false, REF_STRIDE, 28);
        FfpFixture.attrib(3, 2, GL11.GL_FLOAT, false, REF_STRIDE, 36);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        VAOManager.setCurrentVertexFlags(VertexFlags.COLOR_BIT | VertexFlags.TEXTURE_BIT | VertexFlags.BRIGHTNESS_BIT);

        GLSMDraws.glDrawArrays(GL11.GL_QUADS, 0, specs.length * 4);
        GLSMVertexInput.glBindVertexArray(0);
    }

    public static void drawInstanced(Spec[] specs) {
        final ByteBuffer records = BufferUtils.createByteBuffer(specs.length * WeatherInstancedAttribs.STRIDE);
        for (int i = 0; i < specs.length; i++) {
            final Spec s = specs[i];
            WeatherInstancedAttribs.putColumn(records, i, s.x(), s.z(), s.bottom(), s.top(), s.uBase(), s.uDrift(), s.vBase(), s.vDrift(),
                s.brightness(), s.scrollOffset(), s.scrollScale(), s.source(), s.r(), s.g(), s.b(), s.a(),
                s.period(), s.uDriftScale(), s.vDriftScale(), s.fadeScale());
        }

        WeatherParams.set(TRANSLATE_X, TRANSLATE_Y, TRANSLATE_Z, INV_RADIUS, CAMERA_FRAC_X, CAMERA_FRAC_Z,
            PARTIAL_TICKS, TICK + PARTIAL_TICKS, TICK, STRENGTH, PROVIDER_TICK, PROVIDER_TICK + PARTIAL_TICKS, PROVIDER_INV_RADIUS);

        GLSMVertexInput.glBindVertexArray(WeatherQuadMesh.vao());
        if (instanceVbo == 0) instanceVbo = GLSMBuffers.glGenBuffers();
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
        GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, records, GL15.GL_STREAM_DRAW);
        WeatherInstancedAttribs.pointInstanceAttribs(0L);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        VAOManager.setCurrentVertexFlags(WeatherQuadMesh.VERTEX_FLAGS);
        GLSMPrograms.setFfpInstancing(Instancing.WEATHER);
        GLSMDraws.glDrawArraysInstanced(GL11.GL_TRIANGLE_FAN, 0, WeatherQuadMesh.VERTEX_COUNT, specs.length);
        GLSMPrograms.setFfpInstancing(Instancing.NONE);
        GLSMVertexInput.glBindVertexArray(0);
    }

    public static void assertPixelParity(int[] reference, int[] instanced, int size, int background, IntFunction<String> describe) {
        FfpFixture.assertPixelParity("instanced weather columns", reference, instanced, size, background, 16, 0, describe);
    }

    public static void deleteResources() {
        if (instanceVbo != 0) {
            GLSMBuffers.glDeleteBuffers(instanceVbo);
            instanceVbo = 0;
        }
        if (referenceVbo != 0) {
            GLSMBuffers.glDeleteBuffers(referenceVbo);
            referenceVbo = 0;
        }
        if (referenceVao != 0) {
            GLSMVertexInput.glDeleteVertexArrays(referenceVao);
            referenceVao = 0;
        }
        if (weatherTexture != 0) {
            GLSMTextures.glDeleteTextures(weatherTexture);
            weatherTexture = 0;
        }
        if (lightmapTexture != 0) {
            GLSMTextures.glDeleteTextures(lightmapTexture);
            lightmapTexture = 0;
        }
        WeatherQuadMesh.delete();
    }
}
