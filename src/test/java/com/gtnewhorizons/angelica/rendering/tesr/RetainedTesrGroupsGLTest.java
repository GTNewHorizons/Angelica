package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.MatrixHelper;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrShader;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.ffp.CubeParams;
import com.gtnewhorizons.angelica.glsm.ffp.CubeParityFixture;
import com.gtnewhorizons.angelica.glsm.ffp.FfpFixture;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.rendering.GlintClock;
import com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrGroups.Group;
import com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrGroups.InstanceColumns;
import com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrGroups.TexRun;
import com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.CountingPipeline;
import com.gtnewhorizons.angelica.shadercompat.ShaderGlint;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.iris.layer.PassOverride;
import net.coderbot.iris.uniforms.CapturedStateScope;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.util.ResourceLocation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.SCOPE;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.COLOR_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.VERTEX_SIZE;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.X_INDEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class RetainedTesrGroupsGLTest {

    private static final int SIZE = 256;
    private static final int BACKGROUND = 0xFF000000;
    private static final float CUBE_SCALE = 0.1f;
    private static final int COLOR_ABGR = 0xFF0000FF;
    private static final float[] TRIANGLE = { -0.1f, -0.1f, 0f, 0.1f, -0.1f, 0f, 0f, 0.15f, 0f };
    private static final ResourceLocation GLINT_TEXTURE = new ResourceLocation("angelica", "test/glint");

    private static boolean probeRegistered;
    private static Runnable onBufferSetup;

    private final long[] clock = {1_000L};
    private AngelicaBufferSource source;
    private InstanceRing ring;
    private InstancedTemplateRenderer instanced;
    private RetainedTesrGroups groups;
    private RenderLayer layer;

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
    static void registerBufferSetupProbe() {
        if (probeRegistered) return;
        probeRegistered = true;
        VertexFormat.registerSetupBufferStateOverride((format, offset) -> {
            if (onBufferSetup != null) onBufferSetup.run();
            return false;
        });
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
        ShaderManager.enable();
        ffp.activate();

        source = new AngelicaBufferSource();
        ring = new InstanceRing();
        instanced = new InstancedTemplateRenderer(ring);
        groups = new RetainedTesrGroups(source, () -> clock[0]);
        layer = RetainedTesrTestSupport.testLayer();
    }

    @AfterEach
    void cleanup() {
        onBufferSetup = null;
        source.discard();
        GLStateManager.setFfpInstancing(Instancing.NONE);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        groups.clear();
        instanced.clear();
        ring.delete();
        TesrShaderPrograms.invalidateAll();
        final ShaderManager ffp = ShaderManager.getInstance();
        if (ffp.isActive()) ffp.deactivate();
        ShaderManager.disable();
        if (baseTexture != 0) GLStateManager.glDeleteTextures(baseTexture);
        if (glintTexture != 0) GLStateManager.glDeleteTextures(glintTexture);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
        GLStateManager.disableFog();
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
        final TemplateBuffer template = RetainedTesrTestSupport.template();
        groups.beginPass(new Matrix4f(), 0, 0, 0, instanced, pipeline);
        for (int id = 1; id <= 3; id++) {
            final Matrix4f mv = new Matrix4f().translation(id, 0f, 0f);
            groups.queue(SCOPE, template, layer, RetainedTesrTestSupport.STREAM, mv, 0, -1, 0, 0L, id, null);
            groups.queue(SCOPE, template, RetainedTesrTestSupport.cubes(1), 0.0625f, layer, RetainedTesrTestSupport.STREAM, mv, 0, -1, 0, 0L, id, null, null);
        }
    }

    private static final TesrMaterial BAKED_ADDITIVE = TesrMaterial.builder().additiveAlpha().build();
    private static final TesrMaterial BAKED_TRANSLUCENT = TesrMaterial.builder().translucent().build();

    private void bakedFrame(TesrMaterial material, TemplateBuffer template, Matrix4f base, boolean instancing, Matrix4f... models) {
        final Matrix4f mv = new Matrix4f();
        groups.beginPass(base, 0, 0, 0, instancing ? instanced : null);
        for (final Matrix4f model : models) {
            groups.queue(SCOPE, template, layer, material, mv.set(base).mul(model), 0, -1, 0, 0L, 5, null);
        }
        groups.drawLayer(layer);
        instanced.endFrame();
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void staticGroupBakesOnceWhileTheCameraRotates() {
        final TemplateBuffer template = capturedQuad(0.1);
        final Matrix4f[] models = new Matrix4f[256];
        for (int i = 0; i < models.length; i++) {
            models[i] = new Matrix4f().translation((i % 29) * 3.3f - 48f, (i % 7) * 1.7f - 6f, (i % 31) * 3.1f - 48f);
        }
        for (int frame = 0; frame < 120; frame++) {
            clock[0] += 1;
            bakedFrame(BAKED_ADDITIVE, template, new Matrix4f().rotationX(0.1f).rotateY(0.002f * frame), true, models);
        }
        assertEquals(1, groups.rebakes, "rotation alone must not re-bake a static group");
        assertEquals(0, groups.streamPromotions);
    }

    static Stream<Arguments> nonOpaqueMaterials() {
        return Stream.of(Arguments.of("additive", BAKED_ADDITIVE), Arguments.of("translucent", BAKED_TRANSLUCENT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonOpaqueMaterials")
    void nonOpaqueGroupsThatKeepRebakingPromoteToInstancing(String name, TesrMaterial material) {
        final TemplateBuffer template = capturedQuad(0.1);
        for (int frame = 0; frame < 30; frame++) {
            clock[0] += 1;
            bakedFrame(material, template, new Matrix4f(), true, new Matrix4f().translation((frame / 3) * 0.03f, 0f, 0f));
        }
        assertEquals(1, groups.streamPromotions, "a group re-baking every third frame must promote");
        assertEquals(1 + RetainedTesrGroups.PROMOTE_AFTER_REBAKES, groups.rebakes, "the first bake plus the re-bakes that triggered promotion, none after");
        assertTrue(groups.instancedInstances > 0, "a promoted group must draw instanced");
        assertEquals(0, groups.streamedInstances, "promotion with instancing available must not CPU-stream");
    }

    @Test
    void slowRebakesOutsideTheWindowDoNotPromote() {
        final TemplateBuffer template = capturedQuad(0.1);
        for (int frame = 0; frame < 8; frame++) {
            clock[0] += RetainedTesrGroups.PROMOTE_WINDOW_MS + 1;
            bakedFrame(BAKED_ADDITIVE, template, new Matrix4f(), true, new Matrix4f().translation(frame * 0.01f, 0f, 0f));
        }
        assertEquals(0, groups.streamPromotions);
        assertEquals(8, groups.rebakes);
    }

    static Stream<Arguments> reorderRebakes() {
        return Stream.of(Arguments.of("additive keeps its mesh", BAKED_ADDITIVE, 1), Arguments.of("translucent re-bakes every reorder", BAKED_TRANSLUCENT, 8));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reorderRebakes")
    void reorderRebakesOnlyUnderAlphaBlending(String name, TesrMaterial material, int rebakes) {
        final TemplateBuffer template = capturedQuad(0.1);
        final Matrix4f left = new Matrix4f().translation(-0.3f, 0f, 0f);
        final Matrix4f right = new Matrix4f().translation(0.3f, 0f, 0f);
        for (int frame = 0; frame < 8; frame++) {
            clock[0] += 1;
            bakedFrame(material, template, new Matrix4f(), true, frame % 2 == 0 ? left : right, frame % 2 == 0 ? right : left);
        }
        assertEquals(rebakes, groups.rebakes, "draw order matters only under alpha blending");
        assertEquals(0, groups.streamPromotions, "a reorder is not an instance change");
    }

    @ParameterizedTest(name = "count changes {0}")
    @ValueSource(booleans = { true, false })
    void countOrTemplateChangeDoesNotCountTowardPromotion(boolean changeCount) {
        final TemplateBuffer[] templates = {capturedQuad(0.1), capturedQuad(0.2)};
        final Matrix4f left = new Matrix4f().translation(-0.3f, 0f, 0f);
        final Matrix4f right = new Matrix4f().translation(0.3f, 0f, 0f);
        for (int frame = 0; frame < 8; frame++) {
            clock[0] += 1;
            final boolean odd = frame % 2 == 1;
            if (changeCount && odd) {
                bakedFrame(BAKED_ADDITIVE, templates[0], new Matrix4f(), true, left);
            } else {
                bakedFrame(BAKED_ADDITIVE, templates[odd ? 1 : 0], new Matrix4f(), true, left, right);
            }
        }
        assertEquals(8, groups.rebakes, "sanity: every frame re-bakes");
        assertEquals(0, groups.streamPromotions, "instances entering view or swapping meshes are not volatile transforms");
    }

    @ParameterizedTest(name = "{0} distinct templates")
    @ValueSource(ints = { RetainedTesrGroups.PROMOTE_MAX_TEMPLATES, RetainedTesrGroups.PROMOTE_MAX_TEMPLATES + 1 })
    void templateBudgetGatesPromotion(int count) {
        final TemplateBuffer[] templates = new TemplateBuffer[count];
        for (int i = 0; i < count; i++) templates[i] = capturedQuad(0.01);
        for (int frame = 0; frame < 8; frame++) {
            clock[0] += 1;
            groups.beginPass(new Matrix4f(), 0, 0, 0, instanced);
            for (int i = 0; i < count; i++) {
                groups.queue(SCOPE, templates[i], layer, BAKED_ADDITIVE, new Matrix4f().translation(frame * 0.01f, i * 0.05f, 0f), 0, -1, 0, 0L, 5, null);
            }
            groups.drawLayer(layer);
            instanced.endFrame();
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        }
        assertEquals(count <= RetainedTesrGroups.PROMOTE_MAX_TEMPLATES ? 1 : 0, groups.streamPromotions);
    }

    @Test
    void templateRetiredBeforeFirstDrawIsFreed() {
        final TemplateBuffer template = template(TRIANGLE, COLOR_ABGR, GL11.GL_TRIANGLES);
        instanced.retire(template);
        drawSingleInstance(instanced, template, new Matrix4f(), COLOR_ABGR);
        instanced.releaseRetired();
        assertEquals(0, instanced.meshCount(), "a retire that precedes the mesh's first build must still free it");
    }

    static Stream<Arguments> materialsWithoutInstancing() {
        return Stream.of(Arguments.of("opaque", TesrMaterial.CURRENT_STATE, true), Arguments.of("additive", BAKED_ADDITIVE, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("materialsWithoutInstancing")
    void withoutInstancingOnlyOpaqueGroupsPromote(String name, TesrMaterial material, boolean promotes) {
        final TemplateBuffer template = RetainedTesrTestSupport.template();
        for (int frame = 0; frame < 12; frame++) {
            clock[0] += 1;
            bakedFrame(material, template, new Matrix4f(), false, new Matrix4f().translation(frame * 0.01f, 0f, 0f));
        }
        assertEquals(promotes ? 1 : 0, groups.streamPromotions);
        assertEquals(promotes, groups.streamedInstances > 0, "only a promoted group CPU-streams");
    }

    @Test
    void depthEqualOverlayAndItsDepthWriterAlwaysShareAPath() {
        final TemplateBuffer template = capturedQuad(0.1);
        final Matrix4f fixed = new Matrix4f().translation(0.2f, 0f, 0f);
        boolean sawPromoted = false, sawDemotedAgain = false;
        for (int frame = 0; frame < 40; frame++) {
            final boolean animating = frame < 12;
            clock[0] += animating ? 1 : RetainedTesrGroups.STABILITY_PROBE_MS;
            groups.beginPass(new Matrix4f(), 0, 0, 0, instanced);
            groups.queue(SCOPE, template, layer, TesrMaterial.CURRENT_STATE, fixed, 0, -1, 0, 0L, 5, null);
            groups.queue(SCOPE, template, layer, RetainedTesrTestSupport.DEPTH_EQUAL_OVERLAY, fixed, 0, animating ? 0xFF000000 | frame : -1, 0, 0L, 5, null);
            final ObjectArrayList<Group> pair = RetainedTesrTestSupport.groupsOn(groups, layer);
            assertEquals(2, pair.size());
            assertEquals(pair.get(0).streamPath, pair.get(1).streamPath, "base and overlay diverged at frame " + frame);
            if (pair.get(0).streamPath) sawPromoted = true;
            else if (sawPromoted) sawDemotedAgain = true;
            groups.drawLayer(layer);
            instanced.endFrame();
        }
        assertTrue(sawPromoted, "the animating overlay must pull its static depth writer onto the instanced path");
        assertTrue(sawDemotedAgain, "once the overlay settles the pair must return to baked together");
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void coupledPairWithoutInstancingStaysBaked() {
        final TemplateBuffer template = RetainedTesrTestSupport.template();
        for (int frame = 0; frame < 12; frame++) {
            clock[0] += 1;
            groups.beginPass(new Matrix4f(), 0, 0, 0);
            final Matrix4f pose = new Matrix4f().translation(frame * 0.01f, 0f, 0f);
            groups.queue(SCOPE, template, layer, TesrMaterial.CURRENT_STATE, pose, 0, -1, 0, 0L, 5, null);
            groups.queue(SCOPE, template, layer, RetainedTesrTestSupport.DEPTH_EQUAL_OVERLAY, pose, 0, -1, 0, 0L, 5, null);
            final ObjectArrayList<Group> pair = RetainedTesrTestSupport.groupsOn(groups, layer);
            assertEquals(2, pair.size(), "base and overlay must form two groups at frame " + frame);
            assertEquals(pair.get(0).streamPath, pair.get(1).streamPath, "pair diverged at frame " + frame);
            assertFalse(pair.get(0).streamPath, "frame " + frame);
            groups.drawLayer(layer);
        }
        assertEquals(0, groups.streamPromotions, "a depth-coupled group must not promote on its own when its set cannot instance");
        source.discard();
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void cpuFallbackRestoresEveryIdAndTintBeforeDrawingAndRestoresTheCaller() {
        final var state = net.coderbot.iris.uniforms.CapturedRenderingState.INSTANCE;
        try (CapturedStateScope ignored = new CapturedStateScope()) {
            try {
                final var draws = new ArrayList<long[]>();
                onBufferSetup = () -> draws.add(new long[] {
                    state.getCurrentRenderedEntity(), state.getCurrentRenderedBlockEntity(), state.getCurrentRenderedItem(),
                    AngelicaBufferSource.packEntityColor(state.getCurrentEntityColor())
                });
                groups.beginPass(new Matrix4f(), 0, 0, 0, instanced, new CountingPipeline(false, false));
                final TemplateBuffer template = capturedQuad(0.1);
                for (int i = 0; i < 3; i++) {
                    final long info = com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs.packEntityInfo(45010 + i, 45020 + i, 45030 + i);
                    final int color = AngelicaBufferSource.packAbgr(i / 2f, 0, 0, 1);
                    groups.queue(SCOPE, template, layer, RetainedTesrTestSupport.STREAM, new Matrix4f(), 0, -1, color, info, 45020 + i, null);
                }
                state.setCurrentEntityAndItem(81, 82);
                state.setCurrentBlockEntity(83);
                state.setCurrentEntityColor(0, 1, 0, 0.5f);
                draws.clear();
                assertEquals(GL11.GL_QUADS, template.drawMode);
                groups.drawLayer(layer);
                assertEquals(3, draws.size(), "only differing uniform values split the CPU fallback");
                for (int i = 0; i < 3; i++) {
                    assertArrayEquals(new long[] {45010 + i, 45020 + i, 45030 + i,
                        AngelicaBufferSource.packAbgr(i / 2f, 0, 0, 1)}, draws.get(i));
                }
                source.discard();
                assertEquals(81, state.getCurrentRenderedEntity());
                assertEquals(82, state.getCurrentRenderedItem());
                assertEquals(83, state.getCurrentRenderedBlockEntity());
                assertTrue(state.getCurrentEntityColor().equals(0, 1, 0, 0.5f));
            } finally {
                onBufferSetup = null;
                source.discard();
            }
        }
    }

    static Stream<Arguments> variantBinds() {
        return Stream.of(Arguments.of(true, 1, 1, 3, 3), Arguments.of(false, 1, 0, 6, 0));
    }

    @ParameterizedTest(name = "cube variant {0}")
    @MethodSource("variantBinds")
    void bothVariantsBindOncePerLayer(boolean cubeVariant, int matrixBinds, int cubeBinds, int instancedInstances, int cubeInstances) {
        final CountingPipeline pipeline = new CountingPipeline(true, cubeVariant);
        queueThreeEntities(pipeline);

        groups.drawLayer(layer);

        assertEquals(matrixBinds, pipeline.matrixBinds, "one matrix variant bind for the whole layer");
        assertEquals(cubeBinds, pipeline.cubeBinds, "one cube variant bind for the whole layer, none without it");
        assertEquals(2, pipeline.rebinds, "one resolve, one release");
        assertEquals(instancedInstances, groups.instancedInstances, "without a cube variant, cube parts draw as their own templates");
        assertEquals(cubeInstances, groups.cubeInstances);
        assertEquals(2, groups.instancedDraws, "the three ids collapse into one group: one template-run draw, one cube-run draw");
        assertEquals(0, groups.streamedInstances);
    }

    @Test
    void textureMatrixRunsDrawInsideTheSingleBind() {
        final CountingPipeline pipeline = new CountingPipeline(true, true);
        final TemplateBuffer template = RetainedTesrTestSupport.template();
        final Matrix4f matrixA = new Matrix4f().translation(0.5f, 0f, 0f);
        final Matrix4f matrixB = new Matrix4f().translation(0f, 0.5f, 0f);
        final Matrix4f[] texMatrices = {matrixA, matrixB, matrixA};

        groups.beginPass(new Matrix4f(), 0, 0, 0, instanced, pipeline);
        for (int id = 1; id <= 3; id++) {
            final Matrix4f mv = new Matrix4f().translation(id, 0f, 0f);
            groups.queue(SCOPE, template, layer, RetainedTesrTestSupport.STREAM, mv, 0, -1, 0, 0L, id, texMatrices[id - 1]);
            groups.queue(SCOPE, template, RetainedTesrTestSupport.cubes(1), 0.0625f, layer, RetainedTesrTestSupport.STREAM, mv, 0, -1, 0, 0L, id, null, null);
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
        final TemplateBuffer first = RetainedTesrTestSupport.template();
        final TemplateBuffer second = RetainedTesrTestSupport.template();

        groups.beginPass(new Matrix4f(), 0, 0, 0, instanced, pipeline);
        groups.queue(SCOPE, first, layer, EntityMaterials.GLINT, new Matrix4f().translation(1f, 0f, 0f), 0, -1, 0, 0L, 1, null);
        groups.queue(SCOPE, second, layer, EntityMaterials.GLINT, new Matrix4f().translation(2f, 0f, 0f), 0, -1, 0, 0L, 2, null);
        groups.queue(SCOPE, first, layer, EntityMaterials.GLINT, new Matrix4f().translation(3f, 0f, 0f), 0, -1, 0, 0L, 3, null);

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
            groups.queue(SCOPE, template, layer, EntityMaterials.GLINT, mv, 0, -1, 0, 0L, id, texMatrices[id - 1]);
            groups.queue(SCOPE, template, RetainedTesrTestSupport.cubes(1), 0.0625f, layer, EntityMaterials.GLINT, mv, 0, -1, 0, 0L, id, texMatrices[id - 1], null);
        }

        final Group group = RetainedTesrTestSupport.onlyGroup(groups);
        assertEquals(2, group.templateColumns.runs.size(), "sanity: template runs A,B,A merge into two heads");
        assertNotNull(group.templateColumns.runs.get(0).next, "sanity: the template A head must carry a second segment");
        assertEquals(2, group.cubeColumns.runs.size(), "sanity: cube runs A,B,A merge into two heads");
        assertNotNull(group.cubeColumns.runs.get(0).next, "sanity: the cube A head must carry a second segment");
        groups.drawLayer(layer);

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
        assertRed(atTranslation, "translated instance must render red at (" + tx + "," + ty + ")");

        final int[] atOrigin = FfpFixture.readPixel(originX, originY);
        assertTrue(atOrigin[0] < 50 && atOrigin[1] < 50 && atOrigin[2] < 50, "the untranslated origin must stay background, got " + atOrigin[0] + "," + atOrigin[1] + "," + atOrigin[2]);

        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);
        drawSingleInstance(instanced, template, new Matrix4f(), COLOR_ABGR);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "instanced draw must not raise a GL error");

        final int[] identityAtOrigin = FfpFixture.readPixel(originX, originY);
        assertRed(identityAtOrigin, "an identity instance must render red at the origin");
    }

    private void queueTriangleOnClearedTarget() {
        GLStateManager.disableBlend();
        GLStateManager.disableAlphaTest();
        GLStateManager.disableTexture();
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
        FfpFixture.clear();
        queueTriangle(groups, new Matrix4f());
    }

    private void queueTriangle(RetainedTesrGroups target, Matrix4f mv) {
        target.beginPass(new Matrix4f(), 0, 0, 0, instanced);
        target.queue(SCOPE, template(TRIANGLE, COLOR_ABGR, GL11.GL_TRIANGLES), layer, RetainedTesrTestSupport.STREAM,
            mv, 0, COLOR_ABGR, 0, 0L, 0, null);
    }

    /** Runs body with the named fields of a batcher singleton swapped and the state fallback installed. */
    private static void withBatcherFields(Object batcher, Runnable body, Object... namesAndValues) {
        final Object[] saved = new Object[namesAndValues.length / 2];
        for (int i = 0; i < saved.length; i++) saved[i] = Reflect.get(batcher, (String) namesAndValues[2 * i]);
        final Runnable oldFlush = BatchStateGuard.flush;
        try {
            for (int i = 0; i < saved.length; i++) Reflect.set(batcher, (String) namesAndValues[2 * i], namesAndValues[2 * i + 1]);
            BatchStateFallback.install();
            body.run();
        } finally {
            for (int i = 0; i < saved.length; i++) Reflect.set(batcher, (String) namesAndValues[2 * i], saved[i]);
            BatchStateGuard.flush = oldFlush;
        }
    }

    @Test
    void scissorChangeDrainsQueuedGeometryBeforeClippingAndEndsModelBatching() {
        GLStateManager.glScissor(0, 0, 0, 0);
        queueTriangleOnClearedTarget();
        final ModelPartBatcher models = ModelPartBatcher.INSTANCE;
        withBatcherFields(models, () -> {
            GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
            assertTrue(models.isActive(), "batching must resume for the rest of the pass after the flush");
            assertFalse(models.hasQueuedGeometry(), "the flush must drain what was queued before the change");
            assertTrue(FfpFixture.readPixel(400, 300)[0] > 200, "queued geometry must draw before the zero-area scissor takes effect");
        }, "activeSource", source, "activeGroups", groups, "active", true);
    }

    @Test
    void heldEntityTranslucentsSurviveUncapturedStateChange() {
        GLStateManager.glScissor(0, 0, 0, 0);
        queueTriangleOnClearedTarget();
        final AngelicaBufferSource blockEntitySource = new AngelicaBufferSource();
        final RetainedTesrGroups blockEntityGroups = new RetainedTesrGroups(blockEntitySource, () -> clock[0]);
        try {
            queueTriangle(blockEntityGroups, new Matrix4f().translation(0.5f, 0f, 0f));
            final ModelPartBatcher models = ModelPartBatcher.INSTANCE;
            withBatcherFields(models, () -> {
                assertTrue(models.hasQueuedGeometry(), "sanity: the block-entity pass has queued geometry to drain");
                GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
                assertTrue(FfpFixture.readPixel(600, 300)[0] > 200, "the active block-entity batch must draw before the zero-area scissor takes effect");
                assertTrue(FfpFixture.readPixel(400, 300)[0] < 50, "held entity geometry must not draw before deferred");
                assertTrue(models.hasHeldEntities(), "held entity translucents wait for the post-deferred flush");
            }, "entitySource", source, "entityGroups", groups, "entityTranslucentsHeld", true,
                "activeSource", blockEntitySource, "activeGroups", blockEntityGroups, "active", true);
        } finally {
            blockEntitySource.discard();
            blockEntityGroups.clear();
        }
    }

    @Test
    void fogChangeDrainsProviderBatchesAndClearsTheActivePass() {
        queueTriangleOnClearedTarget();
        final TesrBatchRenderer renderer = TesrBatchRenderer.INSTANCE;
        final RetainedTesrGroups[] retained = Reflect.get(renderer, "retained");
        final RetainedTesrGroups oldGroups = retained[0];
        retained[0] = groups;
        try {
            withBatcherFields(renderer, () -> {
                GLStateManager.enableFog();
                assertFalse(renderer.hasPendingGeometry());
                assertTrue(FfpFixture.readPixel(400, 300)[0] > 200, "provider geometry must reach the framebuffer before fog is enabled");
            }, "bufferSource", source, "activePass", 0);
        } finally {
            retained[0] = oldGroups;
        }
    }

    @Test
    void emptyShadowPassSetupKeepsBatchingButRendererSpecificStateDisablesIt() {
        final TesrBatchRenderer renderer = TesrBatchRenderer.INSTANCE;
        withBatcherFields(renderer, () -> {
            net.coderbot.iris.layer.GbufferPrograms.setBlockEntityDefaults();
            assertTrue(renderer.hasActivePass(), "default attributes before the first renderer must not disable shadow batching");
            BatchEligibility.begin(BatchEligibility.SAFE, GLStateManager.drawCalls);
            try {
                GLStateManager.glScissor(0, 0, 8, 8);
                assertFalse(renderer.hasActivePass(), "unsupported renderer state needs fallback even before its first model part");
            } finally {
                assertEquals(BatchEligibility.DENIED, BatchEligibility.end(BatchEligibility.SAFE, GLStateManager.drawCalls));
            }
        }, "bufferSource", source, "activePass", TesrBatchRenderer.PASS_SHADOW);
    }

    @Test
    void mirroredItemKeepsItsOuterFacesAndDepthTestedGlintWhenDrawnLater() {
        final AngelicaTesrMeshCache.GtnhMeshBackend backend = new AngelicaTesrMeshCache.GtnhMeshBackend();
        final Tessellator direct = backend.beginCapture(DefaultVertexFormat.POSITION_TEXTURE_NORMAL);
        ItemRenderer.renderItemIn2D(direct, 1f, 0f, 0f, 1f, 16, 16, 0.0625f);
        final TemplateBuffer icon = backend.endCaptureToTemplate();
        assertNotNull(icon);
        // Red front/back faces must hide the green internal pixel walls.
        for (int i = 0; i < icon.vertexCount; i++) {
            icon.data[i * VERTEX_SIZE + COLOR_INDEX] = i < 8 ? 0xFF0000FF : 0xFF00FF00;
        }
        GLStateManager.glViewport(0, 0, SIZE, SIZE);
        GLStateManager.glCullFace(GL11.GL_BACK);
        GLStateManager.glFrontFace(GL11.GL_CCW);
        GLStateManager.disableCull();
        final RenderLayer base = RetainedTesrTestSupport.capturedLayer(EntityMaterials.DROPPED_ITEM_CUTOUT);
        final RenderLayer glint = RetainedTesrTestSupport.capturedLayer(
            TesrMaterial.builder().glint().depthEqual().noDepthWrite().unlit().stream().build());
        for (float yScale : new float[] { -0.9f, 0.9f }) {
            final Matrix4f mv = new Matrix4f().rotateY(0.55f).scale(0.9f, yScale, 0.9f).translate(-0.5f, -0.5f, 0f);
            final int[][] reference = drawItemAndGlint(icon, mv, base, glint, true);
            final int[][] queued = drawItemAndGlint(icon, mv, base, glint, false);
            assertArrayEquals(reference[0], queued[0], "queued base must preserve its original culling state");
            FfpFixture.assertOverlayFootprint(reference[0], reference[1], BACKGROUND, "item glint");
            assertArrayEquals(reference[1], queued[1], "glint must cover the same visible faces after batching");
        }
        ModelPartBatcher.INSTANCE.clear();
    }

    private int[][] drawItemAndGlint(TemplateBuffer icon, Matrix4f mv, RenderLayer base, RenderLayer glint, boolean reference) {
        GLStateManager.disableLighting();
        GLStateManager.glDepthMask(true);
        FfpFixture.clear();
        GLStateManager.enableCull();
        final int depth = GLStateManager.pushState(StateSet.BATCH);
        base.startDrawing();
        if (reference) GLStateManager.disableCull();
        drawSingleInstance(instanced, icon, mv, 0xFF808080);
        final int[] basePixels = FfpFixture.readRegion(SIZE);
        base.endDrawing();
        glint.startDrawing();
        if (reference) GLStateManager.disableCull();
        assertTrue(GL11.glIsEnabled(GL11.GL_DEPTH_TEST));
        assertEquals(GL11.GL_EQUAL, GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        assertFalse(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        drawSingleInstance(instanced, icon, mv, 0xFF808080);
        final int[] glintPixels = FfpFixture.readRegion(SIZE);
        glint.endDrawing();
        GLStateManager.popStateTo(depth);
        assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE), "the batch bracket restores the flush caller's culling");
        return new int[][] { basePixels, glintPixels };
    }

    private void glintState() {
        GLStateManager.glViewport(0, 0, SIZE, SIZE);
        layer = RenderLayer.tesr(GLINT_TEXTURE, TesrMaterial.CURRENT_STATE, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, DrawState.DISABLED, false, true);

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
        glintCubes = RetainedTesrTestSupport.cubes(1);
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
        cols.addCubes(null, glintCubes, CUBE_SCALE, cubeE1, 0, 0xFFFFFFFF, 0, 0L, null);
        cols.addCubes(null, glintCubes, CUBE_SCALE, cubeE2, 0, 0xFFFFFFFF, 0, 0L, null);
        instanced.drawCubes(cols, run(2, 2 * glintCubes.length));
        instanced.endFrame();
        return FfpFixture.readRegion(SIZE);
    }

    private void drawCubeInstance(Matrix4f mv) {
        final InstanceColumns cols = new InstanceColumns();
        cols.addCubes(null, glintCubes, CUBE_SCALE, mv, 0, 0xFFFFFFFF, 0, 0L, null);
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
        groups.queue(SCOPE, glintTemplate, layer, EntityMaterials.GLINT, templateE1, 0, -1, 0, 0L, 1, glintMatrixA);
        groups.queue(SCOPE, glintTemplate, layer, EntityMaterials.GLINT, templateE1, 0, -1, 0, 0L, 1, glintMatrixB);
        groups.queue(SCOPE, glintTemplate, layer, EntityMaterials.GLINT, templateE2, 0, -1, 0, 0L, 2, glintMatrixA);
        groups.queue(SCOPE, glintTemplate, layer, EntityMaterials.GLINT, templateE2, 0, -1, 0, 0L, 2, glintMatrixB);
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
        groups.queue(SCOPE, null, glintCubes, CUBE_SCALE, layer, EntityMaterials.GLINT, cubeE1, 0, -1, 0, 0L, 1, glintMatrixA, null);
        groups.queue(SCOPE, null, glintCubes, CUBE_SCALE, layer, EntityMaterials.GLINT, cubeE1, 0, -1, 0, 0L, 1, glintMatrixB, null);
        groups.queue(SCOPE, null, glintCubes, CUBE_SCALE, layer, EntityMaterials.GLINT, cubeE2, 0, -1, 0, 0L, 2, glintMatrixA, null);
        groups.queue(SCOPE, null, glintCubes, CUBE_SCALE, layer, EntityMaterials.GLINT, cubeE2, 0, -1, 0, 0L, 2, glintMatrixB, null);
        groups.drawLayer(layer);
        instanced.endFrame();
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "the merged candidate draw must not raise a GL error");
        return FfpFixture.readRegion(SIZE);
    }

    private static String describe(int pixel) {
        return "0x" + Integer.toHexString(pixel);
    }

    @ParameterizedTest(name = "cubes {0}")
    @ValueSource(booleans = { false, true })
    void glintRunsMatchSequentialDrawsUnderAlphaBlend(boolean cubes) {
        glintState();
        final int[] base = cubes ? drawBaseCubes() : drawBaseTemplates();
        final int[] reference = cubes ? cubeReference() : templateReference(false);
        FfpFixture.assertOverlayFootprint(base, reference, BACKGROUND, "glint");

        if (!cubes) {
            final int[] swapped = templateReference(true);
            int differing = 0;
            for (int i = 0; i < reference.length; i++) {
                if (reference[i] != swapped[i]) differing++;
            }
            assertTrue(differing > 0, "swapping e1's k0/k1 draw order under alpha blending must change the rendered " + "pixels, otherwise the parity tests could not detect a reversed emitter order");
        }

        CubeParityFixture.assertPixelParity(reference, cubes ? cubeCandidate() : templateCandidate(), SIZE, BACKGROUND, RetainedTesrGroupsGLTest::describe);
    }

    @ParameterizedTest(name = "cubes {0}")
    @ValueSource(booleans = { false, true })
    void replayedGlintMatchesIndependentPasses(boolean cubes) {
        glintState();
        final int[] reference = cubes ? cubeReference() : templateReference(false);
        if (cubes) drawBaseCubes();
        else drawBaseTemplates();
        setGlintBlendState();
        groups.beginPass(new Matrix4f(), 0, 0, 0, instanced);
        final GlintCapture capture = new GlintCapture();
        for (Matrix4f pose : cubes ? new Matrix4f[] {cubeE1, cubeE2} : new Matrix4f[] {templateE1, templateE2}) {
            applyGlintTexMatrix(glintMatrixA);
            assertTrue(capture.begin(groups));
            if (cubes) groups.queue(SCOPE, null, glintCubes, CUBE_SCALE, layer, EntityMaterials.GLINT, pose, 0, -1, 0, 0L, 0, glintMatrixA, null);
            else groups.queue(SCOPE, glintTemplate, layer, EntityMaterials.GLINT, pose, 0, -1, 0, 0L, 0, glintMatrixA);
            capture.end();
            applyGlintTexMatrix(glintMatrixB);
            if (!cubes) {
                GLStateManager.glColor4f(0.5f, 1, 1, 1);
                assertFalse(capture.replay(groups), "changed layer color must retain the original render call");
                GLStateManager.glColor4f(1, 1, 1, 1);
                GLStateManager.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
                assertFalse(capture.replay(groups), "changed layer blending must retain the original render call");
                setGlintBlendState();
            }
            assertTrue(capture.replay(groups));
            if (!cubes) assertFalse(capture.replay(groups), "a captured pass is replayed only once");
        }
        groups.drawLayer(layer);
        instanced.endFrame();
        CubeParityFixture.assertPixelParity(reference, FfpFixture.readRegion(SIZE), SIZE, BACKGROUND, RetainedTesrGroupsGLTest::describe);
    }

    @Test
    void armorGlintUsesTheBasePoseWithTheGlintMaterial() {
        glintState();
        drawBaseTemplates();
        setGlintBlendState();
        GLStateManager.glBlendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
        applyGlintTexMatrix(glintMatrixA);
        drawSingleInstance(instanced, glintTemplate, templateE1, 0xFFFFFFFF);
        applyGlintTexMatrix(glintMatrixB);
        drawSingleInstance(instanced, glintTemplate, templateE1, 0xFFFFFFFF);
        final int[] reference = FfpFixture.readRegion(SIZE);
        drawBaseTemplates();
        groups.beginPass(new Matrix4f(), 0, 0, 0, instanced);
        final GlintCapture base = new GlintCapture();
        assertTrue(base.begin(groups));
        base.allowBase();
        groups.queue(SCOPE, glintTemplate, layer, EntityMaterials.CUTOUT, templateE1, 0, 0xFF123456, 0, 0L, 0, null);
        base.end();
        setGlintBlendState();
        GLStateManager.glBlendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
        applyGlintTexMatrix(glintMatrixA);
        ModelPartBatcher.onTextureBind(new ResourceLocation("angelica", "test/glint"), glintTexture);
        final ModelPartBatcher batcher = ModelPartBatcher.INSTANCE;
        final RetainedTesrGroups previousGroups = Reflect.get(batcher, "activeGroups");
        Reflect.set(batcher, "activeGroups", groups);
        try {
            final GlintCapture glint = new GlintCapture();
            assertTrue(glint.begin(groups));
            GLStateManager.glTranslatef(0.1f, 0, 0);
            assertFalse(base.copyBase(batcher, groups), "a different parent pose must use the original model call");
            GLStateManager.glLoadIdentity();
            assertTrue(base.copyBase(batcher, groups));
            glint.end();
            assertTrue(glint.replay(groups, glintMatrixB), "the second layer can be queued without rerunning legacy texture setup");
            groups.drawLayer(Reflect.get(batcher, "lastLayer"));
            instanced.endFrame();
            final int[] actual = FfpFixture.readRegion(SIZE);
            int error = 0;
            for (int p = 0; p < actual.length; p++) for (int shift = 0; shift < 24; shift += 8) {
                error = Math.max(error, Math.abs(((actual[p] >>> shift) & 255) - ((reference[p] >>> shift) & 255)));
            }
            assertTrue(error <= 2, "reusing the base pose must retain the glint color; error " + error);
        } finally {
            Reflect.set(batcher, "activeGroups", previousGroups);
        }
    }

    @Test
    void mirroredModelViewUnderCullFrontBatchesLikeTheImmediateDraw() {
        GLStateManager.glViewport(0, 0, SIZE, SIZE);
        GLStateManager.disableTexture();
        GLStateManager.disableBlend();
        GLStateManager.disableAlphaTest();
        GLStateManager.enableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);
        GLStateManager.glShadeModel(GL11.GL_SMOOTH);

        final TemplateBuffer quad = capturedQuad(0.5);
        final Matrix4f mirrored = new Matrix4f().scaling(-1f, 1f, 1f);
        assertTrue(mirrored.determinant() < 0f, "the sky stone chest TESR matrix is mirrored, got determinant " + mirrored.determinant());

        GLStateManager.enableCull();
        GLStateManager.glCullFace(GL11.GL_FRONT);
        final int cullCode = DrawState.liveCull();

        final int center = SIZE / 2 * SIZE + SIZE / 2;
        FfpFixture.clear();
        final int[] blank = FfpFixture.readRegion(SIZE);
        drawSingleInstance(instanced, quad, mirrored, COLOR_ABGR);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "the immediate reference draw must not raise a GL error");
        final int[] reference = FfpFixture.readRegion(SIZE);
        assertTrue(reference[center] != blank[center], "the mirrored quad must survive glCullFace(GL_FRONT) when drawn immediately, otherwise the parity check is vacuous: " + describe(reference[center]));

        final RenderLayer culled = RenderLayer.tesr(null, RetainedTesrTestSupport.STREAM, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, cullCode, false, true);
        FfpFixture.clear();
        groups.beginPass(new Matrix4f(), 0, 0, 0, instanced);
        groups.queue(SCOPE, quad, culled, RetainedTesrTestSupport.STREAM, mirrored, 0, COLOR_ABGR, 0, 0L, 1, null);
        GLStateManager.glCullFace(GL11.GL_BACK);

        source.endBatch(groups);
        instanced.endFrame();
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "the batched draw must not raise a GL error");

        CubeParityFixture.assertPixelParity(reference, FfpFixture.readRegion(SIZE), SIZE, BACKGROUND, RetainedTesrGroupsGLTest::describe);
    }


    @Test
    void flushRestoresCallerStateOnEveryGuardedAxis() {
        GLStateManager.glViewport(0, 0, SIZE, SIZE);
        GLStateManager.disableTexture();
        GLStateManager.disableAlphaTest();

        GLStateManager.enableCull();
        GLStateManager.glCullFace(GL11.GL_FRONT);
        GLStateManager.enableBlend();
        GLStateManager.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        GLStateManager.glDepthMask(false);
        GLStateManager.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glPolygonOffset(1.0f, 1.0f);

        final boolean cullEnabledBefore = GLStateManager.getCullState().isEnabled();
        final int cullFaceBefore = GLStateManager.getPolygonState().getCullFaceMode();
        final boolean glCullEnabledBefore = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        final int glCullFaceBefore = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);

        final boolean blendEnabledBefore = GLStateManager.isEffectiveBlendEnabled();
        final boolean glBlendEnabledBefore = GL11.glIsEnabled(GL11.GL_BLEND);
        final int blendSrcBefore = GLStateManager.getEffectiveBlendState(new BlendState()).getSrcRgb();
        final int glBlendSrcBefore = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);

        final boolean depthMaskBefore = GLStateManager.isEffectiveDepthMaskEnabled();
        final boolean glDepthMaskBefore = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        final boolean offsetFillBefore = GLStateManager.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        final boolean glOffsetFillBefore = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        final float offsetFactorBefore = GLStateManager.getPolygonState().getOffsetFactor();
        final float glOffsetFactorBefore = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);

        final TemplateBuffer quad = capturedQuad(0.5);
        final TesrMaterial opposite = TesrMaterial.builder().build();
        final RenderLayer opposingLayer = RetainedTesrTestSupport.layerFor(opposite);

        groups.beginPass(new Matrix4f(), 0, 0, 0, instanced);
        groups.queue(SCOPE, quad, opposingLayer, RetainedTesrTestSupport.STREAM, new Matrix4f(), 0, COLOR_ABGR, 0, 0L, 1, null);

        source.endBatch(groups);
        instanced.endFrame();
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "the flush must not raise a GL error");

        assertEquals(cullEnabledBefore, GLStateManager.getCullState().isEnabled(), "cache cull enable must be restored");
        assertEquals(cullFaceBefore, GLStateManager.getPolygonState().getCullFaceMode(), "cache cull face must be restored");
        assertEquals(glCullEnabledBefore, GL11.glIsEnabled(GL11.GL_CULL_FACE), "driver cull enable must be restored");
        assertEquals(glCullFaceBefore, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face must be restored");

        assertEquals(blendEnabledBefore, GLStateManager.isEffectiveBlendEnabled(), "cache blend enable must be restored");
        assertEquals(glBlendEnabledBefore, GL11.glIsEnabled(GL11.GL_BLEND), "driver blend enable must be restored");
        assertEquals(blendSrcBefore, GLStateManager.getEffectiveBlendState(new BlendState()).getSrcRgb(), "cache blend func must be restored");
        assertEquals(glBlendSrcBefore, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend func must be restored");

        assertEquals(depthMaskBefore, GLStateManager.isEffectiveDepthMaskEnabled(), "cache depth mask must be restored");
        assertEquals(glDepthMaskBefore, GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "driver depth mask must be restored");

        assertEquals(offsetFillBefore, GLStateManager.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL), "cache polygon offset fill must be restored");
        assertEquals(glOffsetFillBefore, GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL), "driver polygon offset fill must be restored");
        assertEquals(offsetFactorBefore, GLStateManager.getPolygonState().getOffsetFactor(), 0.0001f, "cache polygon offset factor must be restored");
        assertEquals(glOffsetFactorBefore, GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR), 0.0001f, "driver polygon offset factor must be restored");
    }

    private static final int[][] SHADER_PROBES = { { 200, 300 }, { 600, 300 }, { 400, 300 }, { 200, 500 } };

    private static void assertRed(int[] pixel, String what) {
        assertTrue(pixel[0] > 200 && pixel[1] < 50 && pixel[2] < 50, what + ", got " + Arrays.toString(pixel));
    }

    private static RenderLayer noLightmapLayer(TesrMaterial material) {
        return RenderLayer.tesr(null, material, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, DrawState.DISABLED, false, false);
    }

    private RenderLayer queueShaderPair(TesrMaterial material, boolean instancing) {
        final RenderLayer shaderLayer = noLightmapLayer(material);
        final TemplateBuffer template = capturedQuad(0.1);
        FfpFixture.clear();
        groups.beginPass(new Matrix4f(), 0, 0, 0, instancing ? instanced : null);
        groups.queue(SCOPE, template, shaderLayer, material, templateE1, 0, COLOR_ABGR, 0, 0L, 5, null);
        groups.queue(SCOPE, template, shaderLayer, material, templateE2, 0, COLOR_ABGR, 0, 0L, 5, null);
        return shaderLayer;
    }

    private int[][] drawShaderLayer(RenderLayer shaderLayer) {
        shaderLayer.startDrawing();
        groups.drawLayer(shaderLayer);
        shaderLayer.endDrawing();
        instanced.endFrame();
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "shader draw must not raise a GL error");
        final int[][] pixels = new int[SHADER_PROBES.length][];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = FfpFixture.readPixel(SHADER_PROBES[i][0], SHADER_PROBES[i][1]);
        }
        return pixels;
    }

    @Test
    void shaderLayerBindsTheInstancedProgramOnceAndMatchesTheRetainedDraw() {
        final TesrShader shader = RetainedTesrTestSupport.linkedSource("angelica:test_gl_source_shader").shader;
        GLStateManager.disableBlend();
        GLStateManager.disableAlphaTest();

        long bakedBinds = TesrShaderPrograms.bakedBinds;
        long instancedBinds = TesrShaderPrograms.instancedBinds;
        final int[][] instancedPixels = drawShaderLayer(queueShaderPair(TesrMaterial.builder().stream().shader(shader).build(), true));
        assertEquals(1, TesrShaderPrograms.bakedBinds - bakedBinds, "the layer hook binds the baked variant once");
        assertEquals(1, TesrShaderPrograms.instancedBinds - instancedBinds, "the instanced variant binds once for the layer");
        assertEquals(0, GLStateManager.getActiveProgram(), "release must restore the caller's program");
        assertEquals(1, groups.instancedDraws, "both instances share one template run");
        assertEquals(2, groups.instancedInstances);

        bakedBinds = TesrShaderPrograms.bakedBinds;
        instancedBinds = TesrShaderPrograms.instancedBinds;
        final int[][] retainedPixels = drawShaderLayer(queueShaderPair(TesrMaterial.builder().shader(shader).build(), false));
        assertEquals(1, TesrShaderPrograms.bakedBinds - bakedBinds);
        assertEquals(0, TesrShaderPrograms.instancedBinds - instancedBinds, "nothing instanced, so no instanced bind");
        assertEquals(1, groups.instancedDraws, "the baked draw is not an instanced draw");

        for (int i = 0; i < SHADER_PROBES.length; i++) {
            assertArrayEquals(retainedPixels[i], instancedPixels[i], "probe " + i + " differs between the baked and instanced variants");
        }
        assertRed(instancedPixels[0], "instance color must reach the left quad");
        assertRed(instancedPixels[1], "instance color must reach the right quad");
        assertTrue(instancedPixels[2][0] < 50 && instancedPixels[3][0] < 50, "probes off the quads must stay background");
    }

    private void drawDepthEqualPair(TesrShader shader, boolean instancing) {
        drawDepthEqualPair(shader, instancing, null, new double[3]);
    }

    private Group[] drawDepthEqualPair(TesrShader shader, boolean instancing, double[] baseOnlyCam, double[] cam) {
        final TesrMaterial.Builder baseBuilder = TesrMaterial.builder();
        final TesrMaterial.Builder overlayBuilder = TesrMaterial.builder().depthEqual().noDepthWrite();
        if (shader != null) overlayBuilder.shader(shader);
        final TesrMaterial baseMaterial = (instancing ? baseBuilder.stream() : baseBuilder).build();
        final TesrMaterial overlayMaterial = (instancing ? overlayBuilder.stream() : overlayBuilder).build();
        final RenderLayer baseLayer = noLightmapLayer(baseMaterial);
        final RenderLayer overlayLayer = noLightmapLayer(overlayMaterial);
        final TemplateBuffer template = capturedQuad(0.6);
        final String path = instancing ? "instanced" : "baked";

        final Matrix4f savedProjection = new Matrix4f(GLStateManager.getProjectionMatrix());
        GLStateManager.glViewport(0, 0, SIZE, SIZE);
        GLStateManager.setProjectionMatrix(new Matrix4f().setPerspective(1.2217f, 1.3713f, 0.0517f, 173.31f));
        GLStateManager.disableTexture();
        GLStateManager.glDepthMask(true);
        FfpFixture.clear();
        try {
            final Matrix4f view = new Matrix4f().translation(0.0713f, -0.0291f, -2.3177f).rotateX(0.413f);
            final Matrix4f pose = new Matrix4f(view).rotateY(0.917f).rotateZ(0.2311f).translate(0.0137f, 0.0219f, 0.0071f);
            if (baseOnlyCam != null) {
                groups.beginPass(view, baseOnlyCam[0], baseOnlyCam[1], baseOnlyCam[2]);
                groups.queue(SCOPE, template, baseLayer, baseMaterial, pose, 0, 0xFF00FF00, 0, 0L, 5, null);
                baseLayer.startDrawing();
                groups.drawLayer(baseLayer);
                baseLayer.endDrawing();
                assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
                FfpFixture.clear();
            }
            final long instancedBefore = groups.instancedDraws;
            groups.beginPass(view, cam[0], cam[1], cam[2], instancing ? instanced : null);
            groups.queue(SCOPE + 1, template, baseLayer, baseMaterial, pose, 0, 0xFF00FF00, 0, 0L, 5, null);
            groups.queue(SCOPE + 1, template, overlayLayer, overlayMaterial, pose, 0, COLOR_ABGR, 0, 0L, 5, null);
            baseLayer.startDrawing();
            groups.drawLayer(baseLayer);
            baseLayer.endDrawing();
            final int[] base = FfpFixture.readRegion(SIZE);
            overlayLayer.startDrawing();
            assertEquals(GL11.GL_EQUAL, GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
            groups.drawLayer(overlayLayer);
            overlayLayer.endDrawing();
            instanced.endFrame();
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "depth-equal pair must not raise a GL error");
            assertEquals(instancing ? 2 : 0, groups.instancedDraws - instancedBefore, path + ": base and overlay must share the path");

            final int[] overlaid = FfpFixture.readRegion(SIZE);
            int covered = 0;
            int missed = 0;
            for (int i = 0; i < base.length; i++) {
                if (base[i] == BACKGROUND) {
                    assertEquals(BACKGROUND, overlaid[i], path + ": overlay drew outside the base at pixel " + i);
                    continue;
                }
                assertEquals(0xFF00FF00, base[i], path + ": base pixel " + i);
                covered++;
                if (overlaid[i] != COLOR_ABGR) missed++;
            }
            assertTrue(covered > SIZE * SIZE / 20, path + ": base must cover a usable area, covered " + covered);
            assertEquals(0, missed, path + ": depth-equal overlay missed " + missed + " of " + covered + " base pixels");
            return new Group[] { RetainedTesrTestSupport.groupsOn(groups, baseLayer).get(0), RetainedTesrTestSupport.groupsOn(groups, overlayLayer).get(0) };
        } finally {
            GLStateManager.setProjectionMatrix(savedProjection);
        }
    }

    @Test
    void depthEqualSetMembersBakeAgainstOneAnchor() {
        GLStateManager.disableBlend();
        GLStateManager.disableAlphaTest();
        final double[] first = { 1000.3, 70.6, -2000.7 };
        final double[] moved = { 1300.4, 75.8, -1750.4 };
        final Group[] pair = drawDepthEqualPair(null, false, first, moved);
        assertEquals(TesrAnchorMath.anchorCoord(first[0]), pair[0].anchor.x, "sanity: the move stays inside the re-anchor distance, so the base keeps its first anchor");
        assertTrue(pair[0].anchor.sameAs(pair[1].anchor), "an overlay joining later must bake against its writer's anchor");
        assertFalse(pair[0].streamPath || pair[1].streamPath, "sanity: both members are on the baked path");
    }

    @Test
    void failedInstancedRelinkDrawsQueuedInstancesOnTheCpuPath() {
        final TesrShaderPrograms.Entry entry = RetainedTesrTestSupport.linkedSource("angelica:test_gl_relink_failure_shader");
        GLStateManager.disableBlend();
        GLStateManager.disableAlphaTest();
        final RenderLayer shaderLayer = queueShaderPair(TesrMaterial.builder().stream().shader(entry.shader).build(), true);
        assertEquals(1, RetainedTesrTestSupport.onlyGroup(groups).templateColumns.runs.size(), "sanity: both parts queued as instances");

        TesrShaderPrograms.invalidateAll();
        TesrShaderPrograms.link(entry, false, RetainedTesrTestSupport.COLOR_VERTEX, RetainedTesrTestSupport.COLOR_FRAGMENT);
        TesrShaderPrograms.link(entry, true, RetainedTesrTestSupport.BAKED_ONLY_VERTEX, RetainedTesrTestSupport.COLOR_FRAGMENT);
        assertFalse(TesrShaderPrograms.instancedAvailable(entry), "sanity: the relinked instanced variant failed");

        final int[][] pixels = drawShaderLayer(shaderLayer);
        assertEquals(0, groups.instancedDraws, "no instanced program, so no instanced draw");
        assertEquals(2, groups.streamedInstances, "both queued instances must draw through the CPU fallback");
        assertRed(pixels[0], "left instance must reach the framebuffer");
        assertRed(pixels[1], "right instance must reach the framebuffer");
    }

    @Test
    void shaderOverlayDepthEqualsItsEmulatedBaseOnBothPaths() {
        final TesrShader shader = RetainedTesrTestSupport.linkedSource("angelica:test_gl_depth_parity_shader").shader;
        GLStateManager.disableBlend();
        GLStateManager.disableAlphaTest();
        drawDepthEqualPair(shader, false);
        drawDepthEqualPair(shader, true);
    }
}
