package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.MatrixHelper;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrShaders;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.hooks.TextureUtilHooks;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

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
        GLStateManager.disableFog();
        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        GLStateManager.enableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);
        GLStateManager.glColorMask(true, true, true, true);
        GLStateManager.disableBlend();
        GLStateManager.enableCull();
        GLStateManager.enableLighting();
    }

    @AfterEach
    void tearDown() {
        AlphaTestStorage.restoreAlphaTest();
        DepthColorStorage.unlockDepthColor();
        VanillaLayerRig.clear();

        ModelPartBatcher.INSTANCE.clear();
        TesrShaderPrograms.invalidateAll();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        final ShaderManager ffp = ShaderManager.getInstance();
        if (ffp.isActive()) ffp.deactivate();
        ShaderManager.disable();

        GLStateManager.disableBlend();
        GLStateManager.disableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_ALWAYS, 0f);
        GLStateManager.glDepthFunc(GL11.GL_LESS);
        GLStateManager.glDepthMask(true);
        GLStateManager.glColorMask(true, true, true, true);
        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
    }

    private static RenderLayer layer(TesrMaterial material) {
        return RenderLayer.tesr(null, material, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, DrawState.CULL_BACK, true, true);
    }

    @Test
    void batchBracketRestoresCallerDepthAfterGlint() {
        for (boolean enabled : new boolean[] { true, false }) {
            GLStateManager.getDepthTest().setEnabled(enabled);
            GLStateManager.glDepthFunc(GL11.GL_GREATER);
            final int depth = GLStateManager.pushState(StateSet.BATCH);
            final RenderLayer base = layer(EntityMaterials.DROPPED_ITEM_CUTOUT);
            final RenderLayer glint = glint(ShaderGlint.NO_TINT);
            base.startDrawing();
            assertDepth(true, GL11.GL_LEQUAL);
            base.endDrawing();
            glint.startDrawing();
            assertDepth(true, GL11.GL_EQUAL);
            glint.endDrawing();
            GLStateManager.popStateTo(depth);
            assertDepth(enabled, GL11.GL_GREATER);
        }
    }

    private static void assertDepth(boolean enabled, int function) {
        assertEquals(enabled, GLStateManager.getDepthTest().isEnabled(), "cached depth enable");
        assertEquals(enabled, GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "driver depth enable");
        assertEquals(function, GLStateManager.getDepthState().getFunc(), "cached depth function");
        assertEquals(function, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "driver depth function");
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
                GLStateManager.setTextureMatrix(0, new Matrix4f().translation(0.25f, 0f, 0f));
                GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
                GbufferPrograms.pushOverridePhase(WorldRenderingPhase.TERRAIN_CUTOUT);
                return new BatchScenario(
                    () -> {
                        assertTrue(MatrixHelper.isIdentity(GLStateManager.getTextures().getTextureUnitMatrix(0)), "unit 0 texture matrix during batch draw");
                        assertNull(GbufferPrograms.getSpecialCondition(), "special condition during batch draw");
                        assertNull(GbufferPrograms.getOverridePhase(), "override phase during batch draw");
                    },
                    () -> {
                        assertEquals(0.25f, GLStateManager.getTextures().getTextureUnitMatrix(0).m30(), "caller texture matrix restored");
                        assertEquals(SpecialCondition.GLINT, GbufferPrograms.getSpecialCondition(), "caller special condition restored");
                        assertEquals(WorldRenderingPhase.TERRAIN_CUTOUT, GbufferPrograms.getOverridePhase(), "caller override phase restored");
                    },
                    () -> {
                        GbufferPrograms.popOverridePhase();
                        GbufferPrograms.teardownSpecialRenderCondition();
                        GLStateManager.setTextureMatrix(0, new Matrix4f());
                    });
            }),
            Arguments.of("foreign program and framebuffer", (Supplier<BatchScenario>) () -> {
                final int program = GlTestSupport.program(GlTestSupport.TRIVIAL_VS, GlTestSupport.TRIVIAL_FS);
                final int framebuffer = GLStateManager.glGenFramebuffers();
                GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                BatchDrawDefaults.capturePassFramebuffer();
                GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
                GLStateManager.glUseProgram(program);
                return new BatchScenario(
                    () -> {
                        assertEquals(0, GLStateManager.getActiveProgram(), "program during batch draw");
                        assertEquals(0, GLStateManager.getDrawFramebuffer(), "framebuffer during batch draw");
                    },
                    () -> {
                        assertEquals(program, GLStateManager.getActiveProgram(), "caller program restored");
                        assertEquals(framebuffer, GLStateManager.getDrawFramebuffer(), "caller framebuffer restored");
                    },
                    () -> {
                        GLStateManager.glUseProgram(0);
                        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                        GLStateManager.glDeleteFramebuffers(framebuffer);
                        GLStateManager.glDeleteProgram(program);
                    });
            }),
            Arguments.of("filter from before an item renderer's blur window", (Supplier<BatchScenario>) () -> {
                final int texture = GLStateManager.glGenTextures();
                GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                // What TextureUtil.func_152777_a(false, false, 1) does around an item render.
                TextureUtilHooks.captureBoundFilterTexture();
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                return new BatchScenario(
                    () -> assertEquals(GL11.GL_LINEAR, TextureInfoCache.INSTANCE.getInfo(texture).getMinFilter(), "filter from before the window"),
                    () -> assertEquals(GL11.GL_NEAREST, TextureInfoCache.INSTANCE.getInfo(texture).getMinFilter(), "window filter restored"),
                    () -> {
                        TextureUtilHooks.clearSavedFilterTexture();
                        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
                        GLStateManager.glDeleteTextures(texture);
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
        GLStateManager.glCullFace(GL11.GL_BACK);
        GLStateManager.glFrontFace(GL11.GL_CW);
        try {
            final int cull = DrawState.liveCull();
            assertEquals(DrawState.CULL_BACK | DrawState.FRONT_FACE_CLOCKWISE, cull);
            final RenderLayer layer = RenderLayer.tesr(null, TesrMaterial.builder().build(), PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, cull, true, true);
            GLStateManager.glFrontFace(GL11.GL_CCW);
            final int depth = GLStateManager.pushState(StateSet.BATCH);
            layer.startDrawing();
            assertEquals(GL11.GL_CW, GLStateManager.getPolygonState().getFrontFace(), "cached front face");
            assertEquals(GL11.GL_CW, GL11.glGetInteger(GL11.GL_FRONT_FACE), "driver front face");
            assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE), "cull stays on");
            layer.endDrawing();
            GLStateManager.popStateTo(depth);
            assertEquals(GL11.GL_CCW, GLStateManager.getPolygonState().getFrontFace(), "front face restored by BATCH pop");
        } finally {
            GLStateManager.glFrontFace(GL11.GL_CCW);
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
        final int depth = GLStateManager.pushState(StateSet.BATCH);
        layer.startDrawing();
        assertEquals(culled, GL11.glIsEnabled(GL11.GL_CULL_FACE));
        layer.endDrawing();
        GLStateManager.popStateTo(depth);
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
            GLStateManager.disableCull();
            final RenderLayer twoSided = layerFor.apply(material);
            GLStateManager.enableCull();
            final RenderLayer culled = layerFor.apply(material);
            assertNotSame(twoSided, culled, "the last-layer shortcut must include culling");
            GLStateManager.disableCull();
            assertSame(twoSided, layerFor.apply(material), "the layer map must retain both states");
        }

        GLStateManager.disableCull();
        final RenderLayer twoSided = layerFor.apply(materials[0]);
        GLStateManager.enableCull();
        final RenderLayer culled = layerFor.apply(materials[0]);
        twoSided.startDrawing();
        assertFalse(GL11.glIsEnabled(GL11.GL_CULL_FACE));
        assertTrue(GL11.glIsEnabled(GL11.GL_DEPTH_TEST));
        assertTrue(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        twoSided.endDrawing();

        GLStateManager.enableCull();
        drawInBatch(twoSided, false);
        assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE));

        GLStateManager.disableCull();
        drawInBatch(culled, true);
        assertFalse(GL11.glIsEnabled(GL11.GL_CULL_FACE));

        GLStateManager.enableCull();
        drawInBatch(layerFor.apply(TesrMaterial.builder().noCull().build()), false);
        assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE));
    }

    static Stream<Arguments> axisCases() {
        return Stream.of(
            Arguments.of("current state over a dirty caller", (Runnable) () -> {
                    GLStateManager.disableCull();
                    GLStateManager.glCullFace(GL11.GL_FRONT);
                    GLStateManager.disableLighting();
                    GLStateManager.glColorMask(false, false, false, false);
                }, TesrMaterial.CURRENT_STATE, (Runnable) () -> {
                    assertFalse(GLStateManager.getAlphaTest().isEnabled(), "no cutout");
                    assertTrue(GLStateManager.getDepthTest().isEnabled(), "depth test");
                    assertEquals(GL11.GL_LEQUAL, GLStateManager.getDepthState().getFunc());
                    assertTrue(GLStateManager.getDepthState().isEnabled(), "depth write");
                    assertFalse(GLStateManager.getBlendMode().isEnabled(), "opaque");
                    assertTrue(GLStateManager.getCullState().isEnabled(), "cull pinned on");
                    assertEquals(GL11.GL_BACK, GLStateManager.getPolygonState().getCullFaceMode(), "cull face pinned to back");
                    assertTrue(GLStateManager.getLightingState().isEnabled(), "lit");
                    assertTrue(GLStateManager.getColorMask().red, "color mask pinned on");
                    assertFalse(GLStateManager.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL), "no polygon offset");
                }),
            Arguments.of("every material toggle", (Runnable) () -> { },
                TesrMaterial.builder().noCull().unlit().noDepthWrite().cutout(0.5f).translucent().depthEqual().build(), (Runnable) () -> {
                    assertFalse(GLStateManager.getCullState().isEnabled(), "noCull");
                    assertFalse(GLStateManager.getLightingState().isEnabled(), "unlit");
                    assertFalse(GLStateManager.getDepthState().isEnabled(), "noDepthWrite");
                    assertTrue(GLStateManager.getAlphaTest().isEnabled(), "cutout");
                    assertEquals(0.5f, GLStateManager.getAlphaState().getReference(), "cutout threshold");
                    assertTrue(GLStateManager.getBlendMode().isEnabled(), "translucent");
                    assertEquals(GL11.GL_SRC_ALPHA, GLStateManager.getBlendState().getSrcRgb());
                    assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, GLStateManager.getBlendState().getDstRgb());
                    assertEquals(GL11.GL_EQUAL, GLStateManager.getDepthState().getFunc(), "depthEqual");
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
                GLStateManager.getAlphaTest().setVanillaLayer(AlphaTestStorage.ENABLE_LAYER);
                GLStateManager.getAlphaState().setVanillaLayer(AlphaTestStorage.FUNC_LAYER);

                GLStateManager.disableBlend();
                GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
                GLStateManager.enableAlphaTest();
                GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1f);
                GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
                GLStateManager.glDepthMask(true);

                AlphaTestStorage.overrideAlphaTest(null);

                assertSame(EntityMaterials.CUTOUT, EntityMaterials.fromCurrentState(true), "raw alpha test is disabled by the pack override; a raw read would give SOLID");
                assertSame(EntityMaterials.DROPPED_ITEM_CUTOUT, EntityMaterials.itemFromCurrentState(true));
            }),
            Arguments.of("depth mask", (Runnable) () -> {
                GLStateManager.getDepthState().setVanillaLayer(DepthColorStorage.DEPTH_LAYER);
                GLStateManager.getColorMask().setVanillaLayer(DepthColorStorage.COLOR_LAYER);

                GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
                GLStateManager.enableBlend();
                GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
                GLStateManager.glDepthMask(true);

                DepthColorStorage.disableDepthColor();

                assertSame(EntityMaterials.TRANSLUCENT_DEPTH_WRITE, EntityMaterials.fromCurrentState(true), "raw depth mask held off by another pass; the effective vanilla mask must pick the depth-write variant");
            }),
            Arguments.of("color mask", (Runnable) () -> {
                GLStateManager.getDepthState().setVanillaLayer(DepthColorStorage.DEPTH_LAYER);
                GLStateManager.getColorMask().setVanillaLayer(DepthColorStorage.COLOR_LAYER);

                GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
                GLStateManager.disableBlend();
                GLStateManager.enableAlphaTest();
                GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1f);
                GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
                GLStateManager.glDepthMask(true);

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
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        final ShaderManager ffp = ShaderManager.getInstance();
        ShaderManager.enable();
        ffp.activate();

        assertTrue(unitTexIdentity(), "a freshly loaded identity unit-0 matrix must read as identity");

        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glTranslatef(0.3f, 0.1f, 0f);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        assertFalse(unitTexIdentity(), "a translate in texture mode must stop reading as identity");
        assertEquals(new Matrix4f().translation(0.3f, 0.1f, 0f), texMatrixScratch(), "the scratch copy must hold the unit-0 matrix once it stops being identity");

        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        assertTrue(unitTexIdentity(), "loadIdentity after a push must read as identity again");

        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glPopMatrix();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        assertFalse(unitTexIdentity(), "popping back to the earlier translate must stop reading as identity");
        assertEquals(new Matrix4f().translation(0.3f, 0.1f, 0f), texMatrixScratch(), "pop must restore the memo to the popped matrix");

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glTranslatef(0.5f, 0.5f, 0f);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        assertFalse(unitTexIdentity(), "a unit-1 operation forces a recompute, but unit 0 itself is unchanged and stays non-identity");
        assertEquals(new Matrix4f().translation(0.3f, 0.1f, 0f), texMatrixScratch(), "the recompute must copy unit 0, not the active unit 1");

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glTranslatef(0.5f, 0.5f, 0f);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        assertTrue(unitTexIdentity(), "an identity unit 0 must read as identity while a non-identity unit 1 is active");
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
    }
}
