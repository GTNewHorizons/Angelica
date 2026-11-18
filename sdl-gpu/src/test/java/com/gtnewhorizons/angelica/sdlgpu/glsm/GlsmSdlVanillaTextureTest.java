package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextureLifetime;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.SIZE;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.describe;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.pixelAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private static final int RELEASE_DELAY_FRAMES = Reflect.getStatic(GLSMTextureLifetime.class, "RELEASE_DELAY_FRAMES");
    private static final int CLEAR_COLOR = 0xFF000000;

    private static int atlasTexture;
    private static int lightmapTexture;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.beginFrame();
        atlasTexture = createMipmappedAtlas();
        lightmapTexture = allocateDynamicLightmap();
        GlsmSdlHeadlessRig.endFrame();
    }

    private static int createMipmappedAtlas() {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        final int id = GLSMTextures.glGenTextures();
        allocateTextureImpl(id, ATLAS_MIPMAP_LEVELS, ATLAS_SIZE, ATLAS_SIZE, 1.0f);
        for (int level = 0; level <= ATLAS_MIPMAP_LEVELS; level++) {
            uploadTextureSub(level, LEVEL_COLORS[level], ATLAS_SIZE >> level, ATLAS_SIZE >> level, true);
        }
        return id;
    }

    private static int allocateDynamicLightmap() {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        final int id = GLSMTextures.glGenTextures();
        allocateTextureImpl(id, 0, LIGHTMAP_SIZE, LIGHTMAP_SIZE, 1.0f);
        return id;
    }

    private static void allocateTextureImpl(int id, int maxLevel, int width, int height, float aniso) {
        GLSMTextures.glDeleteTextures(id);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, id);

        GLSMTextures.glTexParameterf(GL11.GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);

        if (maxLevel > 0) {
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, maxLevel);
            GLSMTextures.glTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, 0.0f);
            GLSMTextures.glTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, (float) maxLevel);
            GLSMTextures.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 0.0f);
        }

        for (int level = 0; level <= maxLevel; level++) {
            GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA, width >> level, height >> level, 0,
                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (IntBuffer) null);
        }
    }

    private static void uploadTextureSub(int level, int argb, int width, int height, boolean mipmapped) {
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
            mipmapped ? GL11.GL_NEAREST_MIPMAP_LINEAR : GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        final IntBuffer data = BufferUtils.createIntBuffer(width * height);
        for (int i = 0; i < width * height; i++) {
            data.put(i, argb);
        }
        data.position(0);
        GLSMTextures.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, 0, 0, width, height,
            GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, data);
    }

    private static void uploadLightmapFrame(int argb) {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, lightmapTexture);
        uploadTextureSub(0, argb, LIGHTMAP_SIZE, LIGHTMAP_SIZE, false);
    }

    private static void bindAtlasUnit0() {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, atlasTexture);
        GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void enableLightmap() {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        final float scale = 0.00390625f;
        GLSMMatrices.glScalef(scale, scale, scale);
        GLSMMatrices.glTranslatef(8.0f, 8.0f, 8.0f);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, lightmapTexture);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GLSMCurrentAttribs.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private static void quad(float extent, boolean lightmap, boolean counterClockwise) {
        GLSMDraws.glBegin(GL11.GL_QUADS);
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
        GLSMDraws.glEnd();
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

    private static int centerAfterPushAttribWrappedCulledQuad(boolean frontFacing) {
        uploadLightmapFrame(0xFFFFFFFF);
        bindAtlasUnit0();
        enableLightmap();
        GlsmSdlHeadlessRig.terrainRenderState();

        GLSMEnable.glEnable(GL11.GL_CULL_FACE);
        GLSMRasterizer.glCullFace(GL11.GL_BACK);
        GLSMAttribStack.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        quad(1.0f, true, frontFacing);
        GLSMAttribStack.glPopAttrib();

        return center();
    }

    @Test
    void pushAttribWrappedCulledDrawKeepsFrontFace() {
        assertColorNear(LEVEL_COLORS[0], centerAfterPushAttribWrappedCulledQuad(true), 2, "front-face winding kept");
    }

    @Test
    void pushAttribWrappedCulledDrawDropsBackFace() {
        final int actual = centerAfterPushAttribWrappedCulledQuad(false);
        assertEquals(CLEAR_COLOR, actual, () -> "back-face winding was not culled: " + describe(actual));
    }

    @Test
    void displayListWithBindingsOutsideShowsTheModulatedTexel() {
        final int list = GLSMDraws.glGenLists(1);
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        quad(1.0f, true, true);
        GLSMDraws.glEndList();

        uploadLightmapFrame(0xFFFF8040);
        bindAtlasUnit0();
        enableLightmap();
        GlsmSdlHeadlessRig.terrainRenderState();

        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glTranslatef(0.0f, 0.0f, 0.0f);
        GLSMDraws.glCallList(list);
        GLSMMatrices.glPopMatrix();

        final int expected = modulate(LEVEL_COLORS[0], 0xFFFF8040);
        final int actual = center();
        assertColorNear(expected, actual, 2, "display list with bindings outside");
    }

    private static void texImageEmpty(int level) {
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA8, 0, 0, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
    }

    @Test
    void zeroSizedLevelZeroDropsStorageAndRespecifyRecreatesIt() {
        final ResourceManager rm = GlsmSdlHeadlessRig.resourceManager();
        final int tex = GlsmSdlHeadlessRig.newNearestTexture();
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 2);
        GlsmSdlHeadlessRig.uploadSolidLevel(0, 4, LEVEL_COLORS[0]);
        assertEquals(3, rm.getTextureMeta(tex).levels(), "MAX_LEVEL 2 allocates the 4x4 chain");
        final long handle = rm.getTextureHandle(tex);

        texImageEmpty(1);
        assertEquals(handle, rm.getTextureHandle(tex), "zero-sized level 1 must leave storage alone");

        texImageEmpty(0);
        assertEquals(0L, rm.getTextureHandle(tex), "zero-sized level 0 must release the handle");
        assertNull(rm.getTextureMeta(tex), "zero-sized level 0 must drop the meta");
        texImageEmpty(1);
        assertEquals(0L, rm.getTextureHandle(tex), "zero-sized level 1 on an empty texture must not allocate");

        GlsmSdlHeadlessRig.uploadSolidLevel(0, 4, LEVEL_COLORS[0]);
        assertNotEquals(0L, rm.getTextureHandle(tex), "respecified level 0 must recreate storage");
        assertEquals(4, rm.getTextureMeta(tex).width(), "recreated width");
        assertEquals(3, rm.getTextureMeta(tex).levels(), "MAX_LEVEL survives the release");
        GLSMTextures.glDeleteTextures(tex);
    }

    @Test
    void rescuedTextureAllocatesOneLevelUntilAHigherLevelIsUploaded() {
        final ResourceManager rm = GlsmSdlHeadlessRig.resourceManager();
        final int tex = GlsmSdlHeadlessRig.newNearestTexture();
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 2);
        GLSMTextures.glDeleteTextures(tex);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GlsmSdlHeadlessRig.uploadSolidLevel(0, 4, LEVEL_COLORS[0]);
        assertEquals(1, rm.getTextureMeta(tex).levels(), "level-0 upload");
        GlsmSdlHeadlessRig.uploadSolidLevel(1, 2, LEVEL_COLORS[1]);
        assertEquals(3, rm.getTextureMeta(tex).levels(), "level-1 upload grows to the full chain");
        GLSMTextures.glDeleteTextures(tex);
    }

    @Test
    void deletingA2DTextureKeepsTheUnitsNon2DBinding() {
        final ResourceManager rm = GlsmSdlHeadlessRig.resourceManager();
        final int deleted = GlsmSdlHeadlessRig.newNearestTexture();
        GlsmSdlHeadlessRig.uploadSolidLevel(0, 4, LEVEL_COLORS[0]);
        final int volume = GLSMTextures.glGenTextures();
        GLSMTextures.glBindTexture(GL12.GL_TEXTURE_3D, volume);

        GLSMTextures.glDeleteTextures(deleted);
        assertEquals(volume, BackendManager.RENDER_BACKEND.getInteger(GL12.GL_TEXTURE_BINDING_3D), "after the delete");
        for (int i = 0; i < RELEASE_DELAY_FRAMES; i++) GLSMTextureLifetime.onFrameEnd();

        assertEquals(0L, rm.getTextureHandle(deleted), "the frame-end release must have run");
        assertEquals(volume, BackendManager.RENDER_BACKEND.getInteger(GL12.GL_TEXTURE_BINDING_3D), "after the release");
        GLSMTextures.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        GLSMTextures.glDeleteTextures(volume);
    }

}
