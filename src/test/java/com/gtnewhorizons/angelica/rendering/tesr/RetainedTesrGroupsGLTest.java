package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.MatrixHelper;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.CubeParams;
import com.gtnewhorizons.angelica.glsm.ffp.CubeParityFixture;
import com.gtnewhorizons.angelica.glsm.ffp.FfpFixture;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.rendering.GlintClock;
import com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrGroups.InstanceColumns;
import com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrGroups.TexRun;
import com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrGroupsTest.CountingPipeline;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.minecraft.client.renderer.Tessellator;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.COLOR_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.VERTEX_SIZE;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.X_INDEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class RetainedTesrGroupsGLTest {

    private static final int SIZE = 256;
    private static final int BACKGROUND = 0xFF000000;
    private static final float CUBE_SCALE = 0.1f;
    private static final int COLOR_ABGR = 0xFF0000FF;
    private static final float[] TRIANGLE = { -0.1f, -0.1f, 0f, 0.1f, -0.1f, 0f, 0f, 0.15f, 0f };

    private static boolean overridesRegistered;
    private static boolean skipClientArrays;

    private AngelicaBufferSource source;
    private InstanceRing ring;
    private InstancedTemplateRenderer instanced;
    private RetainedTesrGroups groups;
    private RetainedTesrGroupsTest.TestLayer layer;

    private int baseTexture;
    private int glintTexture;
    private Matrix4f glintMatrixA;
    private Matrix4f glintMatrixB;
    private final Matrix4f templateE1 = new Matrix4f().translation(-0.5f, 0f, 0f);
    private final Matrix4f templateE2 = new Matrix4f().translation(0.5f, 0f, 0f);
    private final Matrix4f cubeE1 = new Matrix4f().translation(-0.55f, 0f, 0f);
    private final Matrix4f cubeE2 = new Matrix4f().translation(0.55f, 0f, 0f);
    private TemplateBuffer glintTemplate;
    private CubeParams[] glintCubes;

    @BeforeAll
    static void registerClientArrayOverrides() {
        if (overridesRegistered) return;
        overridesRegistered = true;
        VertexFormat.registerSetupBufferStateOverride((format, offset) -> skipClientArrays);
        VertexFormat.registerClearBufferStateOverride(format -> skipClientArrays);
    }

    @BeforeEach
    void drawableState() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glViewport(0, 0, 800, 600);
        GLStateManager.disableDepthTest();
        GLStateManager.disableCull();
        GLStateManager.disableFog();
        GLStateManager.disableLighting();
        GLStateManager.glColor4f(1f, 1f, 1f, 1f);

        final ShaderManager ffp = ShaderManager.getInstance();
        ffp.enable();
        ffp.activate();

        source = new AngelicaBufferSource();
        ring = new InstanceRing();
        instanced = new InstancedTemplateRenderer(ring);
        groups = new RetainedTesrGroups(source);
        layer = new RetainedTesrGroupsTest.TestLayer();
    }

    @AfterEach
    void cleanup() {
        skipClientArrays = false;
        GLStateManager.ffpInstancing = Instancing.NONE;
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        groups.clear();
        instanced.clear();
        ring.delete();
        final ShaderManager ffp = ShaderManager.getInstance();
        if (ffp.isActive()) ffp.deactivate();
        ffp.disable();
        if (baseTexture != 0) GLStateManager.glDeleteTextures(baseTexture);
        if (glintTexture != 0) GLStateManager.glDeleteTextures(glintTexture);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glDepthMask(true);
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
        GLStateManager.glDisable(GL11.GL_ALPHA_TEST);
    }

    private static TemplateBuffer template(float[] xyz, int colorABGR, int drawMode) {
        final int vertexCount = xyz.length / 3;
        final int[] data = new int[vertexCount * VERTEX_SIZE];
        for (int v = 0; v < vertexCount; v++) {
            final int base = v * VERTEX_SIZE;
            data[base + X_INDEX] = Float.floatToRawIntBits(xyz[v * 3]);
            data[base + X_INDEX + 1] = Float.floatToRawIntBits(xyz[v * 3 + 1]);
            data[base + X_INDEX + 2] = Float.floatToRawIntBits(xyz[v * 3 + 2]);
            data[base + COLOR_INDEX] = colorABGR;
        }
        return new TemplateBuffer(data, vertexCount, drawMode);
    }

    private static TemplateBuffer capturedQuad(double halfExtent) {
        final AngelicaTesrMeshCache.GtnhMeshBackend backend = new AngelicaTesrMeshCache.GtnhMeshBackend();
        final Tessellator direct = backend.beginCapture(DefaultVertexFormat.POSITION_TEXTURE_NORMAL);
        direct.startDrawingQuads();
        direct.addVertexWithUV(-halfExtent, -halfExtent, 0.0, 0.0, 1.0);
        direct.addVertexWithUV(halfExtent, -halfExtent, 0.0, 1.0, 1.0);
        direct.addVertexWithUV(halfExtent, halfExtent, 0.0, 1.0, 0.0);
        direct.addVertexWithUV(-halfExtent, halfExtent, 0.0, 0.0, 0.0);
        direct.draw();
        return backend.endCaptureToTemplate();
    }

    private static TexRun run(int parts, int instances) {
        final TexRun run = new TexRun();
        run.end = parts;
        run.parts = parts;
        run.instances = instances;
        run.last = run;
        return run;
    }

    private static void drawSingleInstance(InstancedTemplateRenderer renderer, TemplateBuffer template, Matrix4fc mv, int colorABGR) {
        final InstanceColumns cols = new InstanceColumns();
        cols.add(template, mv, 0, colorABGR, 0, 0L);
        renderer.drawTemplates(cols, run(1, 1), System.currentTimeMillis());
        renderer.endFrame();
    }

    private void queueThreeEntities(CountingPipeline pipeline) {
        final TemplateBuffer template = RetainedTesrGroupsTest.template();
        groups.beginPass(new Matrix4f(), 0, 0, 0, instanced, pipeline);
        for (int id = 1; id <= 3; id++) {
            final Matrix4f mv = new Matrix4f().translation(id, 0f, 0f);
            groups.queue(template, layer, RetainedTesrGroupsTest.STREAM, mv, 0, -1, 0, 0L, id, null);
            groups.queue(template, RetainedTesrGroupsTest.cubes(1), 0.0625f, layer, RetainedTesrGroupsTest.STREAM, mv, 0, -1, 0, 0L, id, null);
        }
    }

    @Test
    void bothVariantsBindOncePerLayer() {
        final CountingPipeline pipeline = new CountingPipeline(true, true);
        queueThreeEntities(pipeline);

        groups.drawLayer(layer);

        assertEquals(1, pipeline.matrixBinds, "one matrix variant bind for the whole layer");
        assertEquals(1, pipeline.cubeBinds, "one cube variant bind for the whole layer");
        assertEquals(2, pipeline.rebinds, "one resolve, one release");
        assertEquals(3, groups.instancedInstances);
        assertEquals(3, groups.cubeInstances);
        assertEquals(2, groups.instancedDraws, "the three ids collapse into one group: one template-run draw, one cube-run draw");
        assertEquals(0, groups.streamedInstances);
    }

    @Test
    void cubePartsRideTheMatrixVariantWhenTheCubeVariantIsMissing() {
        final CountingPipeline pipeline = new CountingPipeline(true, false);
        queueThreeEntities(pipeline);

        groups.drawLayer(layer);

        assertEquals(1, pipeline.matrixBinds);
        assertEquals(0, pipeline.cubeBinds);
        assertEquals(2, pipeline.rebinds, "one resolve, one release");
        assertEquals(6, groups.instancedInstances, "cube parts draw as their own templates");
        assertEquals(0, groups.cubeInstances);
        assertEquals(0, groups.streamedInstances);
        assertEquals(2, groups.instancedDraws, "one template-run draw, one cube-as-template-run draw");
    }

    @Test
    void textureMatrixRunsDrawInsideTheSingleBind() {
        final CountingPipeline pipeline = new CountingPipeline(true, true);
        final TemplateBuffer template = RetainedTesrGroupsTest.template();
        final Matrix4f matrixA = new Matrix4f().translation(0.5f, 0f, 0f);
        final Matrix4f matrixB = new Matrix4f().translation(0f, 0.5f, 0f);
        final Matrix4f[] texMatrices = {matrixA, matrixB, matrixA};

        groups.beginPass(new Matrix4f(), 0, 0, 0, instanced, pipeline);
        for (int id = 1; id <= 3; id++) {
            final Matrix4f mv = new Matrix4f().translation(id, 0f, 0f);
            groups.queue(template, layer, RetainedTesrGroupsTest.STREAM, mv, 0, -1, 0, 0L, id, texMatrices[id - 1]);
            groups.queue(template, RetainedTesrGroupsTest.cubes(1), 0.0625f, layer, RetainedTesrGroupsTest.STREAM, mv, 0, -1, 0, 0L, id, null);
        }

        groups.drawLayer(layer);

        assertEquals(4, groups.instancedDraws, "three texture-matrix template runs (A,B,A never merge non-adjacent) plus the single identity cube run");
        assertEquals(3, groups.texMatrixRuns, "one non-identity glLoadMatrix per drawn template run, not per distinct matrix value");
        assertEquals(GL11.GL_MODELVIEW, GLStateManager.getMatrixMode().getMode(), "matrix mode must be restored after the texture-matrix runs");
        assertTrue(MatrixHelper.isIdentity(GLStateManager.getTextures().getTextureUnitMatrix(0)), "unit-0 texture matrix must be restored to identity");
    }

    @Test
    void interleavedTemplatesBucketOncePerTemplate() {
        final CountingPipeline pipeline = new CountingPipeline(true, false);
        final TemplateBuffer first = RetainedTesrGroupsTest.template();
        final TemplateBuffer second = RetainedTesrGroupsTest.template();

        groups.beginPass(new Matrix4f(), 0, 0, 0, instanced, pipeline);
        groups.queue(first, layer, EntityMaterials.GLINT, new Matrix4f().translation(1f, 0f, 0f), 0, -1, 0, 0L, 1, null);
        groups.queue(second, layer, EntityMaterials.GLINT, new Matrix4f().translation(2f, 0f, 0f), 0, -1, 0, 0L, 2, null);
        groups.queue(first, layer, EntityMaterials.GLINT, new Matrix4f().translation(3f, 0f, 0f), 0, -1, 0, 0L, 3, null);

        groups.drawLayer(layer);

        assertEquals(2, groups.instancedDraws, "one draw per distinct template, not per template change");
        assertEquals(3, groups.instancedInstances);
    }

    @Test
    void cpuFallbackVisitsEverySegmentOnce() {
        final CountingPipeline pipeline = new CountingPipeline(false, false);
        final TemplateBuffer template = template(new float[] { 0f, 0f, 0f, 1f, 0f, 0f, 2f, 0f, 0f }, 0xFFFFFFFF, GL11.GL_TRIANGLES);
        final Matrix4f matrixA = new Matrix4f().translation(0.5f, 0f, 0f);
        final Matrix4f matrixB = new Matrix4f().translation(0f, 0.5f, 0f);
        final Matrix4f[] texMatrices = {matrixA, matrixB, matrixA};

        groups.beginPass(new Matrix4f(), 0, 0, 0, instanced, pipeline);
        for (int id = 1; id <= 3; id++) {
            final Matrix4f mv = new Matrix4f().translation(id, 0f, 0f);
            groups.queue(template, layer, EntityMaterials.GLINT, mv, 0, -1, 0, 0L, id, texMatrices[id - 1]);
            groups.queue(template, RetainedTesrGroupsTest.cubes(1), 0.0625f, layer, EntityMaterials.GLINT, mv, 0, -1, 0, 0L, id, texMatrices[id - 1]);
        }

        final RetainedTesrGroups.Group group = RetainedTesrGroupsTest.onlyGroup(groups);
        assertEquals(2, group.templateColumns.runs.size(), "sanity: template runs A,B,A merge into two heads");
        assertNotNull(group.templateColumns.runs.get(0).next, "sanity: the template A head must carry a second segment");
        assertEquals(2, group.cubeColumns.runs.size(), "sanity: cube runs A,B,A merge into two heads");
        assertNotNull(group.cubeColumns.runs.get(0).next, "sanity: the cube A head must carry a second segment");

        skipClientArrays = true;
        groups.drawLayer(layer);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}

        assertEquals(0, pipeline.matrixBinds);
        assertEquals(0, pipeline.cubeBinds);
        assertEquals(1, pipeline.rebinds, "one resolve, no release when no variant was bound");
        assertEquals(0, groups.instancedDraws);
        assertEquals(6, groups.streamedInstances, "three template instances plus three cube parts, every chained segment streamed exactly once");
    }

    @Test
    void drawTemplatesBakesThePerInstanceModelviewIntoScreenPosition() {
        GLStateManager.disableBlend();
        GLStateManager.disableAlphaTest();
        GLStateManager.disableTexture();

        final Matrix4f translated = new Matrix4f().translation(0.3f, -0.2f, 0f);
        final int tx = (int) ((translated.m30() * 0.5f + 0.5f) * 800);
        final int ty = (int) ((translated.m31() * 0.5f + 0.5f) * 600);
        final int originX = 400;
        final int originY = 300;
        final TemplateBuffer template = template(TRIANGLE, COLOR_ABGR, GL11.GL_TRIANGLES);

        GLStateManager.glClearColor(0f, 0f, 0f, 1f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);
        drawSingleInstance(instanced, template, translated, COLOR_ABGR);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "instanced draw must not raise a GL error");

        final int[] atTranslation = FfpFixture.readPixel(tx, ty);
        assertTrue(atTranslation[0] > 200 && atTranslation[1] < 50 && atTranslation[2] < 50, "translated instance must render red at (" + tx + "," + ty + "), got " + atTranslation[0] + "," + atTranslation[1] + "," + atTranslation[2]);

        final int[] atOrigin = FfpFixture.readPixel(originX, originY);
        assertTrue(atOrigin[0] < 50 && atOrigin[1] < 50 && atOrigin[2] < 50, "the untranslated origin must stay background, got " + atOrigin[0] + "," + atOrigin[1] + "," + atOrigin[2]);

        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);
        drawSingleInstance(instanced, template, new Matrix4f(), COLOR_ABGR);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "instanced draw must not raise a GL error");

        final int[] identityAtOrigin = FfpFixture.readPixel(originX, originY);
        assertTrue(identityAtOrigin[0] > 200 && identityAtOrigin[1] < 50 && identityAtOrigin[2] < 50, "an identity instance must render red at the origin, got " + identityAtOrigin[0] + "," + identityAtOrigin[1] + "," + identityAtOrigin[2]);
    }

    private void glintState() {
        GLStateManager.glViewport(0, 0, SIZE, SIZE);

        GlintClock.beginFrame(123456789L);
        final float u0 = Reflect.<Float>getStatic(GlintClock.class, "armorU0");
        final float v0 = Reflect.<Float>getStatic(GlintClock.class, "armorV0");
        final float u1 = Reflect.<Float>getStatic(GlintClock.class, "armorU1");
        final float v1 = Reflect.<Float>getStatic(GlintClock.class, "armorV1");
        glintMatrixA = new Matrix4f().scale(1f / 3f).rotateZ((float) Math.toRadians(30.0)).translate(u0, v0, 0f);
        glintMatrixB = new Matrix4f().scale(1f / 3f).rotateZ((float) Math.toRadians(-30.0)).translate(u1, v1, 0f);

        baseTexture = FfpFixture.solidTexture(60, 60, 60, 255);
        glintTexture = createGlintTexture();
        glintTemplate = capturedQuad(0.35);
        glintCubes = RetainedTesrGroupsTest.cubes(1);
    }

    private static int createGlintTexture() {
        final int id = GLStateManager.glGenTextures();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        final ByteBuffer texels = BufferUtils.createByteBuffer(4 * 4 * 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                texels.put((byte) (64 + 48 * x)).put((byte) (64 + 48 * y)).put((byte) 200).put((byte) (64 + 48 * x));
            }
        }
        texels.flip();
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 4, 4, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
        return id;
    }

    private void bindBase() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, baseTexture);
        GLStateManager.enableTexture();
        GLStateManager.disableBlend();
        GLStateManager.disableAlphaTest();
        GLStateManager.enableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);
    }

    private static void setGlintBlendState() {
        GLStateManager.disableAlphaTest();
        GLStateManager.glDepthFunc(GL11.GL_EQUAL);
        GLStateManager.glDepthMask(false);
        GLStateManager.enableBlend();
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void applyGlintTexMatrix(Matrix4f m) {
        bindGlint();
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        final FloatBuffer buf = BufferUtils.createFloatBuffer(16);
        m.get(buf);
        GLStateManager.glLoadMatrix(buf);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private static void restoreTexMatrix() {
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private void bindGlint() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, glintTexture);
    }

    private int[] drawBaseTemplates() {
        FfpFixture.clear();
        bindBase();
        final InstanceColumns cols = new InstanceColumns();
        cols.add(glintTemplate, templateE1, 0, 0xFFFFFFFF, 0, 0L);
        cols.add(glintTemplate, templateE2, 0, 0xFFFFFFFF, 0, 0L);
        instanced.drawTemplates(cols, run(2, 2), System.currentTimeMillis());
        instanced.endFrame();
        return FfpFixture.readRegion(SIZE);
    }

    private int[] drawBaseCubes() {
        FfpFixture.clear();
        bindBase();
        final InstanceColumns cols = new InstanceColumns();
        cols.addCubes(null, glintCubes, CUBE_SCALE, cubeE1, 0, 0xFFFFFFFF, 0, 0L);
        cols.addCubes(null, glintCubes, CUBE_SCALE, cubeE2, 0, 0xFFFFFFFF, 0, 0L);
        instanced.drawCubes(cols, run(2, 2 * glintCubes.length));
        instanced.endFrame();
        return FfpFixture.readRegion(SIZE);
    }

    private void drawCubeInstance(Matrix4f mv) {
        final InstanceColumns cols = new InstanceColumns();
        cols.addCubes(null, glintCubes, CUBE_SCALE, mv, 0, 0xFFFFFFFF, 0, 0L);
        instanced.drawCubes(cols, run(1, glintCubes.length));
        instanced.endFrame();
    }

    private int[] templateReference(boolean swapFirstEmitter) {
        drawBaseTemplates();
        setGlintBlendState();
        applyGlintTexMatrix(swapFirstEmitter ? glintMatrixB : glintMatrixA);
        drawSingleInstance(instanced, glintTemplate, templateE1, 0xFFFFFFFF);
        applyGlintTexMatrix(swapFirstEmitter ? glintMatrixA : glintMatrixB);
        drawSingleInstance(instanced, glintTemplate, templateE1, 0xFFFFFFFF);
        applyGlintTexMatrix(glintMatrixA);
        drawSingleInstance(instanced, glintTemplate, templateE2, 0xFFFFFFFF);
        applyGlintTexMatrix(glintMatrixB);
        drawSingleInstance(instanced, glintTemplate, templateE2, 0xFFFFFFFF);
        restoreTexMatrix();
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "the sequential reference draws must not raise a GL error");
        return FfpFixture.readRegion(SIZE);
    }

    private int[] templateCandidate() {
        drawBaseTemplates();
        setGlintBlendState();
        bindGlint();
        groups.beginPass(new Matrix4f(), 0, 0, 0, instanced);
        groups.queue(glintTemplate, layer, EntityMaterials.GLINT, templateE1, 0, -1, 0, 0L, 1, glintMatrixA);
        groups.queue(glintTemplate, layer, EntityMaterials.GLINT, templateE1, 0, -1, 0, 0L, 1, glintMatrixB);
        groups.queue(glintTemplate, layer, EntityMaterials.GLINT, templateE2, 0, -1, 0, 0L, 2, glintMatrixA);
        groups.queue(glintTemplate, layer, EntityMaterials.GLINT, templateE2, 0, -1, 0, 0L, 2, glintMatrixB);
        groups.drawLayer(layer);
        instanced.endFrame();
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "the merged candidate draw must not raise a GL error");
        return FfpFixture.readRegion(SIZE);
    }

    private int[] cubeReference() {
        drawBaseCubes();
        setGlintBlendState();
        applyGlintTexMatrix(glintMatrixA);
        drawCubeInstance(cubeE1);
        applyGlintTexMatrix(glintMatrixB);
        drawCubeInstance(cubeE1);
        applyGlintTexMatrix(glintMatrixA);
        drawCubeInstance(cubeE2);
        applyGlintTexMatrix(glintMatrixB);
        drawCubeInstance(cubeE2);
        restoreTexMatrix();
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "the sequential reference draws must not raise a GL error");
        return FfpFixture.readRegion(SIZE);
    }

    private int[] cubeCandidate() {
        drawBaseCubes();
        setGlintBlendState();
        bindGlint();
        groups.beginPass(new Matrix4f(), 0, 0, 0, instanced);
        groups.queue(null, glintCubes, CUBE_SCALE, layer, EntityMaterials.GLINT, cubeE1, 0, -1, 0, 0L, 1, glintMatrixA);
        groups.queue(null, glintCubes, CUBE_SCALE, layer, EntityMaterials.GLINT, cubeE1, 0, -1, 0, 0L, 1, glintMatrixB);
        groups.queue(null, glintCubes, CUBE_SCALE, layer, EntityMaterials.GLINT, cubeE2, 0, -1, 0, 0L, 2, glintMatrixA);
        groups.queue(null, glintCubes, CUBE_SCALE, layer, EntityMaterials.GLINT, cubeE2, 0, -1, 0, 0L, 2, glintMatrixB);
        groups.drawLayer(layer);
        instanced.endFrame();
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "the merged candidate draw must not raise a GL error");
        return FfpFixture.readRegion(SIZE);
    }

    private static String describe(int pixel) {
        return "0x" + Integer.toHexString(pixel);
    }

    @Test
    void templateGlintRunsMatchSequentialDrawsUnderAlphaBlend() {
        glintState();
        final int[] base = drawBaseTemplates();
        final int[] reference = templateReference(false);
        FfpFixture.assertOverlayFootprint(base, reference, BACKGROUND, "glint");

        final int[] swapped = templateReference(true);
        int differing = 0;
        for (int i = 0; i < reference.length; i++) {
            if (reference[i] != swapped[i]) differing++;
        }
        assertTrue(differing > 0, "swapping e1's k0/k1 draw order under alpha blending must change the rendered " + "pixels, otherwise the parity tests could not detect a reversed emitter order");

        CubeParityFixture.assertPixelParity(reference, templateCandidate(), SIZE, BACKGROUND, RetainedTesrGroupsGLTest::describe);
    }

    @Test
    void cubeGlintRunsMatchSequentialDrawsUnderAlphaBlend() {
        glintState();
        final int[] base = drawBaseCubes();
        final int[] reference = cubeReference();
        FfpFixture.assertOverlayFootprint(base, reference, BACKGROUND, "glint");
        CubeParityFixture.assertPixelParity(reference, cubeCandidate(), SIZE, BACKGROUND, RetainedTesrGroupsGLTest::describe);
    }
}
