package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.MatrixHelper;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrShaders;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMFog;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.hooks.TextureUtilHooks;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import com.gtnewhorizons.angelica.rendering.SkippedGlintBlock;
import com.gtnewhorizons.angelica.shadercompat.ShaderGlint;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.blending.AlphaTestStorage;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.layer.PassOverride;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.PipelineManager;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
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
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@GLCoreTest
class TesrLayerStateTest {

    @BeforeEach
    void liveState() {
        GLSMFog.disableFog();
        GLSMDepthStencilAlpha.enableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        GLSMDepthStencilAlpha.enableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMBlend.glColorMask(true, true, true, true);
        GLSMBlend.disableBlend();
        GLSMRasterizer.enableCull();
        GLSMLighting.enableLighting();
    }

    @AfterEach
    void tearDown() {
        AlphaTestStorage.restoreAlphaTest();
        DepthColorStorage.unlockDepthColor();
        VanillaLayerRig.clear();

        ModelPartBatcher.INSTANCE.clear();
        TesrShaderPrograms.invalidateAll();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        final ShaderManager ffp = ShaderManager.getInstance();
        if (ffp.isActive()) ffp.deactivate();

        GLSMBlend.disableBlend();
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_ALWAYS, 0f);
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LESS);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMBlend.glColorMask(true, true, true, true);
        GLSMEnable.glDisable(GL11.GL_TEXTURE_2D);
    }

    private static void assertDriverColorMask(boolean expected) {
        final ByteBuffer mask = BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, mask);
        for (int i = 0; i < 4; i++) assertEquals(expected, mask.get(i) != 0, "driver color mask channel " + i);
    }

    private static RenderLayer layer(TesrMaterial material) {
        return RenderLayer.tesr(null, material, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, DrawState.CULL_BACK, true, true);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void batchBracketRestoresCallerDepthAfterGlint(boolean held) {
        for (boolean enabled : new boolean[] { true, false }) {
            GLSMDepthStencilAlpha.getDepthTest().setEnabled(enabled);
            GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_GREATER);
            final int depth = GLSMAttribStack.pushState(StateSet.BATCH);
            final RenderLayer base = layer(EntityMaterials.DROPPED_ITEM_CUTOUT);
            final RenderLayer glint = layer(held ? EntityMaterials.ITEM_GLINT : EntityMaterials.GLINT);
            base.startDrawing();
            assertDepth(true, GL11.GL_LEQUAL);
            base.endDrawing();
            glint.startDrawing();
            assertDepth(true, GL11.GL_EQUAL);
            assertSame(SpecialCondition.GLINT, GbufferPrograms.getSpecialCondition());
            glint.endDrawing();
            assertNull(GbufferPrograms.getSpecialCondition());
            GLSMAttribStack.popStateTo(depth);
            assertDepth(enabled, GL11.GL_GREATER);
        }
    }

    private static void assertDepth(boolean enabled, int function) {
        assertEquals(enabled, GLSMDepthStencilAlpha.getDepthTest().isEnabled(), "cached depth enable");
        assertEquals(enabled, GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "driver depth enable");
        assertEquals(function, GLSMDepthStencilAlpha.getDepthState().getFunc(), "cached depth function");
        assertEquals(function, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "driver depth function");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void skippedDroppedGlintPreservesDepthMaskAndTextureMatrix(boolean depthWrite) {
        final Matrix4f textureMatrix = new Matrix4f().translation(0.13f, 0.27f, 0);
        GLSMMatrices.setTextureMatrix(0, textureMatrix);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMDepthStencilAlpha.glDepthMask(depthWrite);
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        SkippedGlintBlock.applyDroppedItemExitState();
        assertEquals(depthWrite, GLSMDepthStencilAlpha.isEffectiveDepthMaskEnabled());
        assertEquals(textureMatrix, GLSMTextures.getTextures().getTextureUnitMatrix(0));
        assertEquals(GL11.GL_MODELVIEW, GLSMMatrices.getMatrixMode().getMode());
        assertEquals(GL11.GL_SRC_COLOR, GLSMBlend.getBlendState().getSrcAlpha(), "dropped glint blends alpha like color, unlike held");
        assertEquals(GL11.GL_ONE, GLSMBlend.getBlendState().getDstAlpha());
    }

    @Test
    void afterGlintLayerDrawsAfterEveryGlintLayer() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RenderLayer liquid = layer(EntityMaterials.DROPPED_ITEM_CUTOUT);
        final RenderLayer glint = glint(ShaderGlint.NO_TINT);
        final RenderLayer bottle = RenderLayer.tesr(null, EntityMaterials.DROPPED_ITEM_CUTOUT, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, DrawState.CULL_BACK, true, true, true);
        final List<RenderLayer> drawn = new ArrayList<>();
        final AngelicaBufferSource.LayerDrawHook hook = new AngelicaBufferSource.LayerDrawHook() {
            @Override
            public boolean hasDraws(RenderLayer layer) {
                return true;
            }

            @Override
            public void drawLayer(RenderLayer layer) {
                drawn.add(layer);
            }
        };
        try {
            assertNotSame(liquid, bottle);
            assertEquals(TransparencyType.AFTER_GLINT, bottle.getTransparencyType());
            source.declareUse(liquid);
            source.declareUse(bottle);
            source.declareUse(glint);
            source.endBatchWithType(TransparencyType.OPAQUE, hook);
            source.endBatch(hook);
            assertEquals(List.of(liquid, glint, bottle), drawn);
        } finally {
            source.discard();
            source.freeBuffers();
        }
    }

    record BatchScenario(Runnable during, Runnable after, Runnable cleanup) {}

    static Stream<Arguments> batchDrawCases() {
        return Stream.of(
            Arguments.of("texture matrix and iris override", (Supplier<BatchScenario>) () -> {
                GLSMMatrices.setTextureMatrix(0, new Matrix4f().translation(0.25f, 0f, 0f));
                GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
                GbufferPrograms.pushOverridePhase(WorldRenderingPhase.TERRAIN_CUTOUT);
                return new BatchScenario(
                    () -> {
                        assertTrue(MatrixHelper.isIdentity(GLSMTextures.getTextures().getTextureUnitMatrix(0)), "unit 0 texture matrix during batch draw");
                        assertNull(GbufferPrograms.getSpecialCondition(), "special condition during batch draw");
                        assertNull(GbufferPrograms.getOverridePhase(), "override phase during batch draw");
                    },
                    () -> {
                        assertEquals(0.25f, GLSMTextures.getTextures().getTextureUnitMatrix(0).m30(), "caller texture matrix restored");
                        assertEquals(SpecialCondition.GLINT, GbufferPrograms.getSpecialCondition(), "caller special condition restored");
                        assertEquals(WorldRenderingPhase.TERRAIN_CUTOUT, GbufferPrograms.getOverridePhase(), "caller override phase restored");
                    },
                    () -> {
                        GbufferPrograms.popOverridePhase();
                        GbufferPrograms.teardownSpecialRenderCondition();
                        GLSMMatrices.setTextureMatrix(0, new Matrix4f());
                    });
            }),
            Arguments.of("foreign program and framebuffer", (Supplier<BatchScenario>) () -> {
                final int program = GlTestSupport.program(GlTestSupport.TRIVIAL_VS, GlTestSupport.TRIVIAL_FS);
                final int framebuffer = GLSMFramebuffer.glGenFramebuffers();
                GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                BatchDrawDefaults.capturePassFramebuffer();
                GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
                GLSMPrograms.glUseProgram(program);
                return new BatchScenario(
                    () -> {
                        assertEquals(0, GLSMPrograms.getActiveProgram(), "program during batch draw");
                        assertEquals(0, GLSMFramebuffer.getDrawFramebuffer(), "framebuffer during batch draw");
                        assertEquals(0, GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), "driver draw framebuffer during batch draw");
                    },
                    () -> {
                        assertEquals(program, GLSMPrograms.getActiveProgram(), "caller program restored");
                        assertEquals(framebuffer, GLSMFramebuffer.getDrawFramebuffer(), "caller framebuffer restored");
                        assertEquals(program, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), "driver caller program restored");
                        assertEquals(framebuffer, GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), "driver caller framebuffer restored");
                    },
                    () -> {
                        GLSMPrograms.glUseProgram(0);
                        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                        GLSMFramebuffer.glDeleteFramebuffers(framebuffer);
                        GLSMPrograms.glDeleteProgram(program);
                    });
            }),
            Arguments.of("filter from before an item renderer's blur window", (Supplier<BatchScenario>) () -> {
                final int texture = GLSMTextures.glGenTextures();
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
                GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                // What TextureUtil.func_152777_a(false, false, 1) does around an item render.
                TextureUtilHooks.captureBoundFilterTexture();
                GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                return new BatchScenario(
                    () -> {
                        assertEquals(GL11.GL_LINEAR, TextureInfoCache.INSTANCE.getInfo(texture).getMinFilter(), "filter from before the window");
                        assertEquals(GL11.GL_LINEAR, GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER), "driver min filter from before the window");
                        assertEquals(GL11.GL_LINEAR, GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER), "driver mag filter from before the window");
                    },
                    () -> {
                        assertEquals(GL11.GL_NEAREST, TextureInfoCache.INSTANCE.getInfo(texture).getMinFilter(), "window filter restored");
                        assertEquals(GL11.GL_NEAREST, GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER), "driver window min filter restored");
                        assertEquals(GL11.GL_NEAREST, GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER), "driver window mag filter restored");
                    },
                    () -> {
                        TextureUtilHooks.clearSavedFilterTexture();
                        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
                        GLSMTextures.glDeleteTextures(texture);
                    });
            }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("batchDrawCases")
    void batchDrawDoesNotInheritTheInterruptedRenderersState(String name, Supplier<BatchScenario> setup) {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RenderLayer layer = layer(EntityMaterials.DROPPED_ITEM_CUTOUT);
        final int[] draws = { 0 };
        final BatchScenario[] scenario = { null };
        final AngelicaBufferSource.LayerDrawHook hook = new AngelicaBufferSource.LayerDrawHook() {
            @Override
            public boolean hasDraws(RenderLayer candidate) {
                return candidate == layer;
            }

            @Override
            public void drawLayer(RenderLayer drawn) {
                draws[0]++;
                scenario[0].during().run();
            }
        };
        try {
            scenario[0] = setup.get();
            source.declareUse(layer);
            source.endBatch(hook);
            assertEquals(1, draws[0]);
            scenario[0].after().run();
        } finally {
            if (scenario[0] != null) scenario[0].cleanup().run();
            source.discard();
            source.freeBuffers();
        }
    }

    @Test
    void clockwiseFrontFaceIsStoredWithTheLayer() {
        GLSMRasterizer.glCullFace(GL11.GL_BACK);
        GLSMRasterizer.glFrontFace(GL11.GL_CW);
        try {
            final int cull = DrawState.liveCull();
            assertEquals(DrawState.CULL_BACK | DrawState.FRONT_FACE_CLOCKWISE, cull);
            final RenderLayer layer = RenderLayer.tesr(null, TesrMaterial.builder().build(), PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, cull, true, true);
            GLSMRasterizer.glFrontFace(GL11.GL_CCW);
            final int depth = GLSMAttribStack.pushState(StateSet.BATCH);
            layer.startDrawing();
            assertEquals(GL11.GL_CW, GLSMRasterizer.getPolygonState().getFrontFace(), "cached front face");
            assertEquals(GL11.GL_CW, GL11.glGetInteger(GL11.GL_FRONT_FACE), "driver front face");
            assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE), "cull stays on");
            layer.endDrawing();
            GLSMAttribStack.popStateTo(depth);
            assertEquals(GL11.GL_CCW, GLSMRasterizer.getPolygonState().getFrontFace(), "front face restored by BATCH pop");
        } finally {
            GLSMRasterizer.glFrontFace(GL11.GL_CCW);
        }
    }

    private static RenderLayer glint(int slot) {
        return RenderLayer.tesr(null, EntityMaterials.GLINT, PassOverride.NONE, 0f, 0f, slot, DrawState.CULL_BACK, true, true);
    }

    @Test
    void failedHookSetupRunsTeardownAndSuppressesItsFailure() {
        final int[] releases = { 0 };
        final RuntimeException setup = new RuntimeException("setup");
        final RuntimeException teardown = new RuntimeException("teardown");
        final TesrMaterial material = TesrMaterial.builder()
            .shader(TesrShaders.register("angelica:test_failed_hook_setup", () -> { throw setup; }, () -> {
                releases[0]++;
                throw teardown;
            }))
            .build();

        final RuntimeException thrown = assertThrows(RuntimeException.class, () -> layer(material).startDrawing());
        assertSame(setup, thrown);
        assertEquals(1, releases[0]);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(teardown, thrown.getSuppressed()[0]);
    }

    @Test
    void shaderLayerDefersToThePassHookWhileAPackIsActive() {
        final List<String> calls = new ArrayList<>();
        final TesrMaterial material = TesrMaterial.builder()
            .shader(TesrShaders.register("angelica:test_pack_defers", () -> calls.add("bind"), () -> calls.add("release")))
            .build();
        final PassOverride pass = PassOverride.of(SpecialCondition.BEACON_BEAM, null, null);
        final RenderLayer layer = RenderLayer.tesr(null, material, pass, 0f, 0f, ShaderGlint.NO_TINT, DrawState.CULL_BACK, true, true);
        withPack(true, drawing -> {
            layer.startDrawing();
            assertSame(SpecialCondition.BEACON_BEAM, GbufferPrograms.getSpecialCondition(), "the pass hook must run in the shader's place");
            drawing[0] = false;
            layer.endDrawing();
            assertNull(GbufferPrograms.getSpecialCondition(), "end must undo what begin ran, not re-read the pack state");
            assertTrue(calls.isEmpty(), "a pack draws the material; the mod program is never bound: " + calls);

            layer.startDrawing();
            drawing[0] = true;
            layer.endDrawing();
            assertEquals(List.of("bind", "release"), calls, "the same interned layer binds the shader once the pack stops drawing the world");
        });
    }

    private static void withPack(boolean initiallyDrawing, Consumer<boolean[]> body) {
        final PipelineManager manager = Iris.getPipelineManager();
        final WorldRenderingPipeline previous = manager.getPipelineNullable();
        final boolean enabled = Iris.enabled;
        final boolean[] drawing = { initiallyDrawing };
        final DeferredWorldRenderingPipeline pipeline = mock(DeferredWorldRenderingPipeline.class);
        when(pipeline.shouldOverrideShaders()).thenAnswer(inv -> drawing[0]);
        Reflect.set(manager, "pipeline", pipeline);
        Reflect.setStaticFinal(Iris.class, "enabled", boolean.class, true);
        try {
            body.accept(drawing);
        } finally {
            Reflect.setStaticFinal(Iris.class, "enabled", boolean.class, enabled);
            Reflect.set(manager, "pipeline", previous);
        }
    }

    private static RenderLayer queuedLayer(TesrMaterial material) {
        return Reflect.invoke(TesrBatchRenderer.INSTANCE, "layerFor",
            new Class<?>[] { ResourceLocation.class, TesrMaterial.class, PassOverride.class, float.class, float.class, int.class, boolean.class, boolean.class },
            null, material, PassOverride.NONE, 0f, 0f, DrawState.liveCull(material), DrawState.liveLit(material), DrawState.liveLightmap());
    }

    private static void drawInBatch(RenderLayer layer, boolean culled) {
        final int depth = GLSMAttribStack.pushState(StateSet.BATCH);
        layer.startDrawing();
        assertEquals(culled, GL11.glIsEnabled(GL11.GL_CULL_FACE));
        layer.endDrawing();
        GLSMAttribStack.popStateTo(depth);
    }

    static Stream<Arguments> layerCaches() {
        return Stream.of(
            Arguments.of("ModelPartBatcher", (Function<TesrMaterial, RenderLayer>) RetainedTesrTestSupport::capturedLayer,
                new TesrMaterial[] { EntityMaterials.DROPPED_ITEM_CUTOUT, EntityMaterials.GLINT }),
            Arguments.of("TesrBatchRenderer", (Function<TesrMaterial, RenderLayer>) TesrLayerStateTest::queuedLayer,
                new TesrMaterial[] { TesrMaterial.CURRENT_STATE }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("layerCaches")
    void queuedCullingSplitsLayerCachesAndRestoresTheDrawCaller(String name, Function<TesrMaterial, RenderLayer> layerFor, TesrMaterial[] materials) {
        for (TesrMaterial material : materials) {
            GLSMRasterizer.disableCull();
            final RenderLayer twoSided = layerFor.apply(material);
            GLSMRasterizer.enableCull();
            final RenderLayer culled = layerFor.apply(material);
            assertNotSame(twoSided, culled, "the last-layer shortcut must include culling");
            GLSMRasterizer.disableCull();
            assertSame(twoSided, layerFor.apply(material), "the layer map must retain both states");
        }

        GLSMRasterizer.disableCull();
        final RenderLayer twoSided = layerFor.apply(materials[0]);
        GLSMRasterizer.enableCull();
        final RenderLayer culled = layerFor.apply(materials[0]);
        twoSided.startDrawing();
        assertFalse(GL11.glIsEnabled(GL11.GL_CULL_FACE));
        assertTrue(GL11.glIsEnabled(GL11.GL_DEPTH_TEST));
        assertTrue(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        twoSided.endDrawing();

        GLSMRasterizer.enableCull();
        drawInBatch(twoSided, false);
        assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE));

        GLSMRasterizer.disableCull();
        drawInBatch(culled, true);
        assertFalse(GL11.glIsEnabled(GL11.GL_CULL_FACE));

        GLSMRasterizer.enableCull();
        drawInBatch(layerFor.apply(TesrMaterial.builder().noCull().build()), false);
        assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE));
    }

    static Stream<Arguments> axisCases() {
        return Stream.of(
            Arguments.of("current state over a dirty caller", (Runnable) () -> {
                    GLSMRasterizer.disableCull();
                    GLSMRasterizer.glCullFace(GL11.GL_FRONT);
                    GLSMLighting.disableLighting();
                    GLSMBlend.glColorMask(false, false, false, false);
                }, TesrMaterial.CURRENT_STATE, (Runnable) () -> {
                    assertFalse(GLSMDepthStencilAlpha.getAlphaTest().isEnabled(), "no cutout");
                    assertTrue(GLSMDepthStencilAlpha.getDepthTest().isEnabled(), "depth test");
                    assertEquals(GL11.GL_LEQUAL, GLSMDepthStencilAlpha.getDepthState().getFunc());
                    assertTrue(GLSMDepthStencilAlpha.getDepthState().isEnabled(), "depth write");
                    assertFalse(GLSMBlend.getBlendMode().isEnabled(), "opaque");
                    assertTrue(GLSMRasterizer.getCullState().isEnabled(), "cull pinned on");
                    assertEquals(GL11.GL_BACK, GLSMRasterizer.getPolygonState().getCullFaceMode(), "cull face pinned to back");
                    assertTrue(GLSMLighting.getLightingState().isEnabled(), "lit");
                    assertTrue(GLSMBlend.getColorMask().red, "color mask pinned on");
                    assertFalse(GLSMEnable.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL), "no polygon offset");
                    assertTrue(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "driver depth test");
                    assertEquals(GL11.GL_LEQUAL, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "driver depth func");
                    assertTrue(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "driver depth write");
                    assertFalse(GL11.glIsEnabled(GL11.GL_BLEND), "driver opaque");
                    assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE), "driver cull pinned on");
                    assertEquals(GL11.GL_BACK, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face pinned to back");
                    assertDriverColorMask(true);
                    assertFalse(GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL), "driver no polygon offset");
                }),
            Arguments.of("every material toggle", (Runnable) () -> { },
                TesrMaterial.builder().noCull().unlit().noDepthWrite().cutout(0.5f).translucent().depthEqual().build(), (Runnable) () -> {
                    assertFalse(GLSMRasterizer.getCullState().isEnabled(), "noCull");
                    assertFalse(GLSMLighting.getLightingState().isEnabled(), "unlit");
                    assertFalse(GLSMDepthStencilAlpha.getDepthState().isEnabled(), "noDepthWrite");
                    assertTrue(GLSMDepthStencilAlpha.getAlphaTest().isEnabled(), "cutout");
                    assertEquals(0.5f, GLSMDepthStencilAlpha.getAlphaState().getReference(), "cutout threshold");
                    assertTrue(GLSMBlend.getBlendMode().isEnabled(), "translucent");
                    assertEquals(GL11.GL_SRC_ALPHA, GLSMBlend.getBlendState().getSrcRgb());
                    assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, GLSMBlend.getBlendState().getDstRgb());
                    assertEquals(GL11.GL_EQUAL, GLSMDepthStencilAlpha.getDepthState().getFunc(), "depthEqual");
                    assertFalse(GL11.glIsEnabled(GL11.GL_CULL_FACE), "driver noCull");
                    assertFalse(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "driver noDepthWrite");
                    assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "driver translucent");
                    assertEquals(GL11.GL_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb");
                    assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), "driver blend dst rgb");
                    assertEquals(GL11.GL_EQUAL, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "driver depthEqual");
                }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("axisCases")
    void everyAxisAppliesAbsolutely(String name, Runnable callerState, TesrMaterial material, Runnable asserts) {
        callerState.run();
        layer(material).startDrawing();
        asserts.run();
    }

    static Stream<Arguments> effectiveStateCases() {
        return Stream.of(
            Arguments.of("alpha test", (Runnable) () -> {
                GLSMDepthStencilAlpha.getAlphaTest().setVanillaLayer(AlphaTestStorage.ENABLE_LAYER);
                GLSMDepthStencilAlpha.getAlphaState().setVanillaLayer(AlphaTestStorage.FUNC_LAYER);

                GLSMBlend.disableBlend();
                GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
                GLSMDepthStencilAlpha.enableAlphaTest();
                GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.1f);
                GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
                GLSMDepthStencilAlpha.glDepthMask(true);

                AlphaTestStorage.overrideAlphaTest(null);

                assertSame(EntityMaterials.CUTOUT, EntityMaterials.fromCurrentState(true), "raw alpha test is disabled by the pack override; a raw read would give SOLID");
                assertSame(EntityMaterials.DROPPED_ITEM_CUTOUT, EntityMaterials.itemFromCurrentState(true));
            }),
            Arguments.of("depth mask", (Runnable) () -> {
                GLSMDepthStencilAlpha.getDepthState().setVanillaLayer(DepthColorStorage.DEPTH_LAYER);
                GLSMBlend.getColorMask().setVanillaLayer(DepthColorStorage.COLOR_LAYER);

                GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
                GLSMBlend.enableBlend();
                GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
                GLSMDepthStencilAlpha.glDepthMask(true);

                DepthColorStorage.disableDepthColor();

                assertSame(EntityMaterials.TRANSLUCENT_DEPTH_WRITE, EntityMaterials.fromCurrentState(true), "raw depth mask held off by another pass; the effective vanilla mask must pick the depth-write variant");
            }),
            Arguments.of("color mask", (Runnable) () -> {
                GLSMDepthStencilAlpha.getDepthState().setVanillaLayer(DepthColorStorage.DEPTH_LAYER);
                GLSMBlend.getColorMask().setVanillaLayer(DepthColorStorage.COLOR_LAYER);

                GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
                GLSMBlend.disableBlend();
                GLSMDepthStencilAlpha.enableAlphaTest();
                GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.1f);
                GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
                GLSMDepthStencilAlpha.glDepthMask(true);

                DepthColorStorage.disableDepthColor();
                assertSame(EntityMaterials.CUTOUT, EntityMaterials.fromCurrentState(true), "raw color mask held off by another pass is not a depth-only draw");

                DepthColorStorage.deferColorMask(false, false, false, false);
                assertSame(EntityMaterials.DEPTH_ONLY_CUTOUT, EntityMaterials.fromCurrentState(true), "vanilla color mask off while held must pick depth-only");
            }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("effectiveStateCases")
    void effectiveStateSurvivesAnotherPassOverridingTheRawState(String name, Runnable body) {
        body.run();
    }

    private static boolean unitTexIdentity() {
        return Reflect.<Boolean>invoke(ModelPartBatcher.INSTANCE, "unitTexIdentity", new Class<?>[0]);
    }

    private static Matrix4f texMatrixScratch() {
        return Reflect.get(ModelPartBatcher.INSTANCE, "texMatrixScratch");
    }

    @Test
    void memoTracksTextureModeOperationsOnUnitZero() {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        final ShaderManager ffp = ShaderManager.getInstance();
        ShaderManager.enable();
        ffp.activate();

        assertTrue(unitTexIdentity(), "a freshly loaded identity unit-0 matrix must read as identity");

        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glTranslatef(0.3f, 0.1f, 0f);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        assertFalse(unitTexIdentity(), "a translate in texture mode must stop reading as identity");
        assertEquals(new Matrix4f().translation(0.3f, 0.1f, 0f), texMatrixScratch(), "the scratch copy must hold the unit-0 matrix once it stops being identity");

        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        assertTrue(unitTexIdentity(), "loadIdentity after a push must read as identity again");

        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glPopMatrix();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        assertFalse(unitTexIdentity(), "popping back to the earlier translate must stop reading as identity");
        assertEquals(new Matrix4f().translation(0.3f, 0.1f, 0f), texMatrixScratch(), "pop must restore the memo to the popped matrix");

        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glTranslatef(0.5f, 0.5f, 0f);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        assertFalse(unitTexIdentity(), "a unit-1 operation forces a recompute, but unit 0 itself is unchanged and stays non-identity");
        assertEquals(new Matrix4f().translation(0.3f, 0.1f, 0f), texMatrixScratch(), "the recompute must copy unit 0, not the active unit 1");

        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMMatrices.glTranslatef(0.5f, 0.5f, 0f);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        assertTrue(unitTexIdentity(), "an identity unit 0 must read as identity while a non-identity unit 1 is active");
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
    }
}
