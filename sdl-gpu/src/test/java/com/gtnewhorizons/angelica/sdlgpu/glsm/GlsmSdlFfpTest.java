package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.SIZE;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.describe;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.pixelAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlFfpTest {

    private static final int TEXEL = 0xFFFF4020;
    private static final int LIGHTMAP_SIZE = 16;

    private static int baseTexture;
    private static int lightmapTexture;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
        GlsmSdlHeadlessRig.beginFrame();
        baseTexture = GlsmSdlHeadlessRig.createSolidTexture(TEXEL);
        lightmapTexture = createLightmapTexture();
        GlsmSdlHeadlessRig.endFrame();
    }

    private static int createLightmapTexture() {
        final int id = GLStateManager.glGenTextures();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, LIGHTMAP_SIZE, LIGHTMAP_SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        final ByteBuffer texels = MemoryUtil.memAlloc(LIGHTMAP_SIZE * LIGHTMAP_SIZE * 4);
        try {
            for (int y = 0; y < LIGHTMAP_SIZE; y++) {
                for (int x = 0; x < LIGHTMAP_SIZE; x++) {
                    final boolean brightest = (x == 0 && y == 0) || (x == LIGHTMAP_SIZE - 1 && y == LIGHTMAP_SIZE - 1);
                    texels.put((byte) (brightest ? 255 : 0));
                    texels.put((byte) 255);
                    texels.put((byte) (brightest ? 255 : 0));
                    texels.put((byte) 255);
                }
            }
            texels.flip();
            GLStateManager.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, LIGHTMAP_SIZE, LIGHTMAP_SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
        } finally {
            MemoryUtil.memFree(texels);
        }
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        return id;
    }

    private static void bindBaseTexture() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, baseTexture);
        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void enableLightmapUnit(boolean vanillaTextureMatrix) {
        GlsmSdlHeadlessRig.enableLightmapUnit(lightmapTexture, vanillaTextureMatrix);
    }

    private static void quadUnit0Only() {
        GLStateManager.glBegin(GL11.GL_QUADS);
        GlsmSdlHeadlessRig.emitVertex(-1.0f, -1.0f, 0.0f, 0.0f, false, (short) 0);
        GlsmSdlHeadlessRig.emitVertex(1.0f, -1.0f, 1.0f, 0.0f, false, (short) 0);
        GlsmSdlHeadlessRig.emitVertex(1.0f, 1.0f, 1.0f, 1.0f, false, (short) 0);
        GlsmSdlHeadlessRig.emitVertex(-1.0f, 1.0f, 0.0f, 1.0f, false, (short) 0);
        GLStateManager.glEnd();
    }

    private static void quadTwoUnits(boolean vanillaBrightness) {
        final short brightness = (short) (vanillaBrightness ? 240 : 0);
        GLStateManager.glBegin(GL11.GL_QUADS);
        GlsmSdlHeadlessRig.emitVertex(-1.0f, -1.0f, 0.0f, 0.0f, true, brightness);
        GlsmSdlHeadlessRig.emitVertex(1.0f, -1.0f, 1.0f, 0.0f, true, brightness);
        GlsmSdlHeadlessRig.emitVertex(1.0f, 1.0f, 1.0f, 1.0f, true, brightness);
        GlsmSdlHeadlessRig.emitVertex(-1.0f, 1.0f, 0.0f, 1.0f, true, brightness);
        GLStateManager.glEnd();
    }

    private static void assertTexelEverywhere(String label) {
        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        final int center = pixelAt(pixels, SIZE, SIZE / 2, SIZE / 2);
        final int corner = pixelAt(pixels, SIZE, 2, 2);
        assertEquals(TEXEL, center, () -> label + ": center " + describe(center));
        assertEquals(TEXEL, corner, () -> label + ": corner " + describe(corner));
    }

    @Test
    void singleUnitTexturedQuadShowsTheTexel() {
        bindBaseTexture();
        quadUnit0Only();
        assertTexelEverywhere("single unit");
    }

    @Test
    void twoUnitTerrainQuadWithVanillaLightmapMatrixShowsTheTexel() {
        bindBaseTexture();
        enableLightmapUnit(true);
        GlsmSdlHeadlessRig.terrainRenderState();
        quadTwoUnits(true);
        assertTexelEverywhere("two units, vanilla lightmap matrix");
    }

    @Test
    void twoUnitTerrainQuadWithIdentityLightmapMatrixShowsTheTexel() {
        bindBaseTexture();
        enableLightmapUnit(false);
        GlsmSdlHeadlessRig.terrainRenderState();
        quadTwoUnits(false);
        assertTexelEverywhere("two units, identity lightmap matrix, zero brightness");
    }

    @Test
    void twoUnitTerrainQuadThroughDisplayListsShowsTheTexel() {
        bindBaseTexture();
        enableLightmapUnit(true);
        GlsmSdlHeadlessRig.terrainRenderState();

        final int list = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(0.0f, 0.0f, 0.0f);
        quadTwoUnits(true);
        GLStateManager.glPopMatrix();
        GLStateManager.glEndList();

        GLStateManager.glCallList(list);
        assertTexelEverywhere("two units, glCallList");

        GlsmSdlHeadlessRig.beginFrame();
        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.clearTo(0.0f, 0.0f, 0.0f, 1.0f);

        final IntBuffer lists = BufferUtils.createIntBuffer(1);
        lists.put(0, list);
        lists.position(0);
        GLStateManager.glCallLists(lists);
        assertTexelEverywhere("two units, glCallLists");
    }

    @Test
    void litTexturedQuadIsShaded() {
        bindBaseTexture();
        enableGuiStandardItemLighting();
        GLStateManager.glEnable(GL12.GL_RESCALE_NORMAL);
        quadUnit0Only();

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        final int center = pixelAt(pixels, SIZE, SIZE / 2, SIZE / 2);
        final int red = (center >> 16) & 0xFF;
        assertEquals(255, (center >>> 24) & 0xFF, () -> "lit quad alpha: " + describe(center));
        assertTrue(red > 100, () -> "lit quad red: " + describe(center));
        assertTrue(red < ((TEXEL >> 16) & 0xFF), () -> "lit quad is not attenuated: " + describe(center));
    }

    private static void enableGuiStandardItemLighting() {
        GLStateManager.glPushMatrix();
        GLStateManager.glRotatef(-30.0f, 0.0f, 1.0f, 0.0f);
        GLStateManager.glRotatef(165.0f, 1.0f, 0.0f, 0.0f);

        GLStateManager.glEnable(GL11.GL_LIGHTING);
        GLStateManager.glEnable(GL11.GL_LIGHT0);
        GLStateManager.glEnable(GL11.GL_LIGHT1);
        GLStateManager.glEnable(GL11.GL_COLOR_MATERIAL);
        GLStateManager.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);

        final float[] first = normalize(0.2f, 1.0f, -0.7f);
        final float[] second = normalize(-0.2f, 1.0f, 0.7f);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, floats(first[0], first[1], first[2], 0.0f));
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, floats(0.6f, 0.6f, 0.6f, 1.0f));
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_AMBIENT, floats(0.0f, 0.0f, 0.0f, 1.0f));
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_SPECULAR, floats(0.0f, 0.0f, 0.0f, 1.0f));
        GLStateManager.glLight(GL11.GL_LIGHT1, GL11.GL_POSITION, floats(second[0], second[1], second[2], 0.0f));
        GLStateManager.glLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, floats(0.6f, 0.6f, 0.6f, 1.0f));
        GLStateManager.glLight(GL11.GL_LIGHT1, GL11.GL_AMBIENT, floats(0.0f, 0.0f, 0.0f, 1.0f));
        GLStateManager.glLight(GL11.GL_LIGHT1, GL11.GL_SPECULAR, floats(0.0f, 0.0f, 0.0f, 1.0f));
        GLStateManager.glShadeModel(GL11.GL_FLAT);
        GLStateManager.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, floats(0.4f, 0.4f, 0.4f, 1.0f));

        GLStateManager.glPopMatrix();
    }

    private static float[] normalize(float x, float y, float z) {
        final float len = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{ x / len, y / len, z / len };
    }

    private static FloatBuffer floats(float a, float b, float c, float d) {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(4);
        buf.put(a).put(b).put(c).put(d);
        buf.flip();
        return buf;
    }
}
