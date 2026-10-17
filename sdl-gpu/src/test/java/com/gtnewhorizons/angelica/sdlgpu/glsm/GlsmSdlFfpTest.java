package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.CubeParityFixture;
import com.gtnewhorizons.angelica.glsm.ffp.FfpFixture;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.ffp.ParticleParityFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.SIZE;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.TEXEL;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.describe;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.pixelAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlFfpTest {

    private static int baseTexture;
    private static int lightmapTexture;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.beginFrame();
        baseTexture = GlsmSdlHeadlessRig.createSolidTexture(TEXEL);
        lightmapTexture = GlsmSdlHeadlessRig.createLightmapTexture(true);
        GlsmSdlHeadlessRig.endFrame();
    }

    @AfterEach
    void cleanupInstancing() {
        GLStateManager.setFfpInstancing(Instancing.NONE);
        CubeParityFixture.deleteResources();
        ParticleParityFixture.deleteResources();
        FfpFixture.EntityIdInstances.delete();
    }

    private static void bindBaseTexture() {
        GlsmSdlHeadlessRig.bindBaseTexture(baseTexture);
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
        GlsmSdlHeadlessRig.quadTwoUnits((short) (vanillaBrightness ? 240 : 0));
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

    /** Vanilla pairs its lightmap texture matrix with brightness 240; the identity matrix case uses brightness 0. */
    @ParameterizedTest(name = "vanilla lightmap matrix and brightness: {0}")
    @ValueSource(booleans = {true, false})
    void twoUnitTerrainQuadShowsTheTexel(boolean vanilla) {
        bindBaseTexture();
        enableLightmapUnit(vanilla);
        GlsmSdlHeadlessRig.terrainRenderState();
        quadTwoUnits(vanilla);
        assertTexelEverywhere(vanilla ? "two units, vanilla lightmap matrix" : "two units, identity lightmap matrix, zero brightness");
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

        GlsmSdlHeadlessRig.beginFrameAndClear();

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

    @Test
    void unitCubeMatchesBakedQuadsWithLightmap() {
        CubeParityFixture.setupScene(true, false);

        final CubeParityFixture.Spec[] specs = CubeParityFixture.specs();

        CubeParityFixture.drawReference(specs, true);
        final int[] reference = GlsmSdlHeadlessRig.readTarget();

        GlsmSdlHeadlessRig.beginFrameAndClear();
        CubeParityFixture.drawInstanced(specs);
        final int[] instanced = GlsmSdlHeadlessRig.readTarget();

        CubeParityFixture.assertPixelParity(reference, instanced, SIZE, 0xFF000000, GlsmSdlHeadlessRig::describe);
    }

    @Test
    void particleFirstPersonRotationMatchesReference() {
        final ParticleParityFixture.Rotation rot = ParticleParityFixture.Rotation.of(35f, -12f, false);
        ParticleParityFixture.setupScene(false);

        final ParticleParityFixture.Particle[] particles = ParticleParityFixture.particles();

        ParticleParityFixture.drawReference(particles, rot.x(), rot.xz(), rot.z(), rot.yz(), rot.xy());
        final int[] reference = GlsmSdlHeadlessRig.readTarget();

        GlsmSdlHeadlessRig.beginFrameAndClear();
        ParticleParityFixture.drawInstanced(particles, rot.x(), rot.xz(), rot.z(), rot.yz(), rot.xy());
        final int[] instanced = GlsmSdlHeadlessRig.readTarget();

        ParticleParityFixture.assertPixelParity(reference, instanced, SIZE, 0xFF000000, GlsmSdlHeadlessRig::describe);
    }

    @Test
    void entityAttribSelectsColorPerInstance() {
        FfpFixture.EntityIdInstances.build(0.6f);

        GlsmSdlHeadlessRig.beginFrameAndClear();

        FfpFixture.EntityIdInstances.draw();

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();

        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, 13, SIZE / 2, GlsmSdlHeadlessRig.RED, "entity -1 must render red");
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, 32, SIZE / 2, GlsmSdlHeadlessRig.GREEN, "entity 7 must render green");
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, 51, SIZE / 2, GlsmSdlHeadlessRig.BLUE, "entity 50020 must render blue");
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
