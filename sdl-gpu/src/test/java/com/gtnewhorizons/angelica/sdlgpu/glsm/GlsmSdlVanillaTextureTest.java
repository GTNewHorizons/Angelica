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
import org.lwjgl.opengl.GL14;

import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.SIZE;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.describe;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.pixelAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlVanillaTextureTest {

    private static final int GL_TEXTURE_MAX_ANISOTROPY_EXT = 34046;

    private static final int ATLAS_SIZE = 64;
    private static final int ATLAS_MIPMAP_LEVELS = 4;
    private static final int[] LEVEL_COLORS = {
        0xFFFF4020,
        0xFF00FF00,
        0xFF0000FF,
        0xFF20C080,
        0xFFFFFFFF,
    };

    private static final int LIGHTMAP_SIZE = 16;
    private static final int CLEAR_COLOR = 0xFF000000;

    private static int atlasTexture;
    private static int lightmapTexture;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
        GlsmSdlHeadlessRig.beginFrame();
        atlasTexture = createMipmappedAtlas();
        lightmapTexture = allocateDynamicLightmap();
        GlsmSdlHeadlessRig.endFrame();
    }

    private static int createMipmappedAtlas() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        final int id = GLStateManager.glGenTextures();
        allocateTextureImpl(id, ATLAS_MIPMAP_LEVELS, ATLAS_SIZE, ATLAS_SIZE, 1.0f);
        for (int level = 0; level <= ATLAS_MIPMAP_LEVELS; level++) {
            uploadTextureSub(level, LEVEL_COLORS[level], ATLAS_SIZE >> level, ATLAS_SIZE >> level, true);
        }
        return id;
    }

    private static int allocateDynamicLightmap() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        final int id = GLStateManager.glGenTextures();
        allocateTextureImpl(id, 0, LIGHTMAP_SIZE, LIGHTMAP_SIZE, 1.0f);
        return id;
    }

    private static void allocateTextureImpl(int id, int maxLevel, int width, int height, float aniso) {
        GLStateManager.glDeleteTextures(id);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);

        GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);

        if (maxLevel > 0) {
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, maxLevel);
            GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, 0.0f);
            GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, (float) maxLevel);
            GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 0.0f);
        }

        for (int level = 0; level <= maxLevel; level++) {
            GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA, width >> level, height >> level, 0,
                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (IntBuffer) null);
        }
    }

    private static void uploadTextureSub(int level, int argb, int width, int height, boolean mipmapped) {
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
            mipmapped ? GL11.GL_NEAREST_MIPMAP_LINEAR : GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        final IntBuffer data = BufferUtils.createIntBuffer(width * height);
        for (int i = 0; i < width * height; i++) {
            data.put(i, argb);
        }
        data.position(0);
        GLStateManager.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, 0, 0, width, height,
            GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, data);
    }

    private static void uploadLightmapFrame(int argb) {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, lightmapTexture);
        uploadTextureSub(0, argb, LIGHTMAP_SIZE, LIGHTMAP_SIZE, false);
    }

    private static void bindAtlasUnit0() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, atlasTexture);
        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void enableLightmap() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        final float scale = 0.00390625f;
        GLStateManager.glScalef(scale, scale, scale);
        GLStateManager.glTranslatef(8.0f, 8.0f, 8.0f);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, lightmapTexture);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private static void quad(float extent, boolean lightmap, boolean counterClockwise) {
        GLStateManager.glBegin(GL11.GL_QUADS);
        if (counterClockwise) {
            GlsmSdlHeadlessRig.emitVertex(-extent, -extent, 0.0f, 0.0f, lightmap, (short) 240);
            GlsmSdlHeadlessRig.emitVertex(extent, -extent, 1.0f, 0.0f, lightmap, (short) 240);
            GlsmSdlHeadlessRig.emitVertex(extent, extent, 1.0f, 1.0f, lightmap, (short) 240);
            GlsmSdlHeadlessRig.emitVertex(-extent, extent, 0.0f, 1.0f, lightmap, (short) 240);
        } else {
            GlsmSdlHeadlessRig.emitVertex(-extent, extent, 0.0f, 1.0f, lightmap, (short) 240);
            GlsmSdlHeadlessRig.emitVertex(extent, extent, 1.0f, 1.0f, lightmap, (short) 240);
            GlsmSdlHeadlessRig.emitVertex(extent, -extent, 1.0f, 0.0f, lightmap, (short) 240);
            GlsmSdlHeadlessRig.emitVertex(-extent, -extent, 0.0f, 0.0f, lightmap, (short) 240);
        }
        GLStateManager.glEnd();
    }

    private static int center() {
        return pixelAt(GlsmSdlHeadlessRig.readTarget(), SIZE, SIZE / 2, SIZE / 2);
    }

    private static void assertColorNear(int expected, int actual, int tolerance, String label) {
        assertEquals(255, (actual >>> 24) & 0xFF, () -> label + ": alpha " + describe(actual));
        for (int shift = 0; shift <= 16; shift += 8) {
            final int e = (expected >> shift) & 0xFF;
            final int a = (actual >> shift) & 0xFF;
            assertTrue(Math.abs(e - a) <= tolerance,
                () -> label + ": expected " + describe(expected) + " got " + describe(actual));
        }
    }

    private static int modulate(int a, int b) {
        int out = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            final int product = Math.round((((a >> shift) & 0xFF) / 255.0f) * (((b >> shift) & 0xFF) / 255.0f) * 255.0f);
            out |= product << shift;
        }
        return out;
    }

    @Test
    void mipmappedAtlasMagnifiedShowsLevelZero() {
        bindAtlasUnit0();
        quad(1.0f, false, true);

        final int actual = center();
        assertColorNear(LEVEL_COLORS[0], actual, 1, "magnified level 0");
    }

    @Test
    void mipmappedAtlasMinifiedShowsALowerLevel() {
        bindAtlasUnit0();
        quad(0.125f, false, true);

        final int actual = center();
        assertEquals(255, (actual >>> 24) & 0xFF, () -> "minified alpha: " + describe(actual));
        assertNotEquals(CLEAR_COLOR, actual, () -> "minified is the clear color: " + describe(actual));
        assertNotEquals(LEVEL_COLORS[0], actual, () -> "minified is level 0: " + describe(actual));
        assertColorNear(LEVEL_COLORS[3], actual, 1, "minified");
    }

    @Test
    void perFrameDynamicLightmapModulatesTheAtlas() {
        final int[] frames = { 0xFFFFFFFF, 0xFFFF8040, 0xFF40FF80 };
        for (int frame = 0; frame < frames.length; frame++) {
            if (frame > 0) GlsmSdlHeadlessRig.beginFrameAndReset();

            uploadLightmapFrame(frames[frame]);
            bindAtlasUnit0();
            enableLightmap();
            GlsmSdlHeadlessRig.terrainRenderState();
            quad(1.0f, true, true);

            final int expected = modulate(LEVEL_COLORS[0], frames[frame]);
            final int actual = center();
            assertColorNear(expected, actual, 2, "dynamic lightmap frame " + frame);
        }
    }

    @Test
    void pushAttribWrappedCulledDrawKeepsFrontFace() {
        uploadLightmapFrame(0xFFFFFFFF);
        bindAtlasUnit0();
        enableLightmap();
        GlsmSdlHeadlessRig.terrainRenderState();

        GLStateManager.glEnable(GL11.GL_CULL_FACE);
        GLStateManager.glCullFace(GL11.GL_BACK);
        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        quad(1.0f, true, true);
        GLStateManager.glPopAttrib();

        final int actual = center();
        assertColorNear(LEVEL_COLORS[0], actual, 2, "front-face winding kept");
    }

    @Test
    void pushAttribWrappedCulledDrawDropsBackFace() {
        uploadLightmapFrame(0xFFFFFFFF);
        bindAtlasUnit0();
        enableLightmap();
        GlsmSdlHeadlessRig.terrainRenderState();

        GLStateManager.glEnable(GL11.GL_CULL_FACE);
        GLStateManager.glCullFace(GL11.GL_BACK);
        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        quad(1.0f, true, false);
        GLStateManager.glPopAttrib();

        final int actual = center();
        assertEquals(CLEAR_COLOR, actual, () -> "back-face winding was not culled: " + describe(actual));
    }

    @Test
    void displayListWithBindingsOutsideShowsTheModulatedTexel() {
        final int list = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        quad(1.0f, true, true);
        GLStateManager.glEndList();

        uploadLightmapFrame(0xFFFF8040);
        bindAtlasUnit0();
        enableLightmap();
        GlsmSdlHeadlessRig.terrainRenderState();

        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(0.0f, 0.0f, 0.0f);
        GLStateManager.glCallList(list);
        GLStateManager.glPopMatrix();

        final int expected = modulate(LEVEL_COLORS[0], 0xFFFF8040);
        final int actual = center();
        assertColorNear(expected, actual, 2, "display list with bindings outside");
    }
}
