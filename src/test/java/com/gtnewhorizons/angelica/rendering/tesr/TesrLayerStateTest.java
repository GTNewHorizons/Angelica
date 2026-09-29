package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.MatrixHelper;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrShaders;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.hooks.TextureUtilHooks;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import com.gtnewhorizons.angelica.shadercompat.ShaderGlint;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.iris.gl.blending.AlphaTestStorage;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.layer.PassOverride;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        GLStateManager.disableBlend();
        GLStateManager.enableCull();
        GLStateManager.enableLighting();
    }

    @AfterEach
    void tearDown() {
        AlphaTestStorage.restoreAlphaTest();
        DepthColorStorage.unlockDepthColor();
        GLStateManager.getAlphaTest().setVanillaLayer(null);
        GLStateManager.getAlphaState().setVanillaLayer(null);
        GLStateManager.getDepthState().setVanillaLayer(null);

        ModelPartBatcher.INSTANCE.clear();
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
    void entityBatchRestoresDepthBeforeBlockEntities() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RenderLayer entityLayer = layer(EntityMaterials.DROPPED_ITEM_CUTOUT);
        final AngelicaBufferSource.LayerDrawHook hook = new AngelicaBufferSource.LayerDrawHook() {
            @Override
            public boolean hasDraws(RenderLayer layer) {
                return layer == entityLayer;
            }

            @Override
            public void drawLayer(RenderLayer layer) {
                assertTrue(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "entity draw depth test");
            }
        };
        try {
            source.declareUse(entityLayer);
            source.endBatchWithType(TransparencyType.OPAQUE, hook);
            source.endBatch(hook);
            assertTrue(GLStateManager.getDepthTest().isEnabled(), "cached depth before block entities");
            assertTrue(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "driver depth before block entities");
        } finally {
            source.discard();
            source.freeBuffers();
        }
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

    @Test
    void batchDrawDoesNotInheritTheInterruptedRenderersTextureMatrixOrIrisOverride() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RenderLayer layer = layer(EntityMaterials.DROPPED_ITEM_CUTOUT);
        final int[] draws = { 0 };
        final AngelicaBufferSource.LayerDrawHook hook = new AngelicaBufferSource.LayerDrawHook() {
            @Override
            public boolean hasDraws(RenderLayer candidate) {
                return candidate == layer;
            }

            @Override
            public void drawLayer(RenderLayer drawn) {
                draws[0]++;
                assertTrue(MatrixHelper.isIdentity(GLStateManager.getTextures().getTextureUnitMatrix(0)), "unit 0 texture matrix during batch draw");
                assertNull(GbufferPrograms.getSpecialCondition(), "special condition during batch draw");
                assertNull(GbufferPrograms.getOverridePhase(), "override phase during batch draw");
            }
        };
        GLStateManager.setTextureMatrix(0, new Matrix4f().translation(0.25f, 0f, 0f));
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
        GbufferPrograms.pushOverridePhase(WorldRenderingPhase.TERRAIN_CUTOUT);
        try {
            source.declareUse(layer);
            source.endBatch(hook);
            assertEquals(1, draws[0]);
            assertEquals(0.25f, GLStateManager.getTextures().getTextureUnitMatrix(0).m30(), "caller texture matrix restored");
            assertEquals(SpecialCondition.GLINT, GbufferPrograms.getSpecialCondition(), "caller special condition restored");
            assertEquals(WorldRenderingPhase.TERRAIN_CUTOUT, GbufferPrograms.getOverridePhase(), "caller override phase restored");
        } finally {
            GbufferPrograms.popOverridePhase();
            GbufferPrograms.teardownSpecialRenderCondition();
            GLStateManager.setTextureMatrix(0, new Matrix4f());
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

    @Test
    void batchDrawSwapsOutAForeignProgramAndFramebuffer() {
        final int program = linkTrivialProgram();
        final int framebuffer = GLStateManager.glGenFramebuffers();
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RenderLayer layer = layer(EntityMaterials.DROPPED_ITEM_CUTOUT);
        final int[] draws = { 0 };
        final AngelicaBufferSource.LayerDrawHook hook = new AngelicaBufferSource.LayerDrawHook() {
            @Override
            public boolean hasDraws(RenderLayer candidate) {
                return candidate == layer;
            }

            @Override
            public void drawLayer(RenderLayer drawn) {
                draws[0]++;
                assertEquals(0, GLStateManager.getActiveProgram(), "program during batch draw");
                assertEquals(0, GLStateManager.getDrawFramebuffer(), "framebuffer during batch draw");
            }
        };
        try {
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            BatchDrawDefaults.capturePassFramebuffer();
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GLStateManager.glUseProgram(program);
            source.declareUse(layer);
            source.endBatch(hook);
            assertEquals(1, draws[0]);
            assertEquals(program, GLStateManager.getActiveProgram(), "caller program restored");
            assertEquals(framebuffer, GLStateManager.getDrawFramebuffer(), "caller framebuffer restored");
        } finally {
            GLStateManager.glUseProgram(0);
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GLStateManager.glDeleteFramebuffers(framebuffer);
            GLStateManager.glDeleteProgram(program);
            source.discard();
            source.freeBuffers();
        }
    }

    @Test
    void batchDrawUsesTheFilterFromBeforeAnItemRenderersBlurWindow() {
        final int texture = GLStateManager.glGenTextures();
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RenderLayer layer = layer(EntityMaterials.DROPPED_ITEM_CUTOUT);
        final int[] draws = { 0 };
        final AngelicaBufferSource.LayerDrawHook hook = new AngelicaBufferSource.LayerDrawHook() {
            @Override
            public boolean hasDraws(RenderLayer candidate) {
                return candidate == layer;
            }

            @Override
            public void drawLayer(RenderLayer drawn) {
                draws[0]++;
                assertEquals(GL11.GL_LINEAR, TextureInfoCache.INSTANCE.getInfo(texture).getMinFilter(), "filter from before the window");
            }
        };
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            // What TextureUtil.func_152777_a(false, false, 1) does around an item render.
            TextureUtilHooks.captureBoundFilterTexture();
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            source.declareUse(layer);
            source.endBatch(hook);
            assertEquals(1, draws[0]);
            assertEquals(GL11.GL_NEAREST, TextureInfoCache.INSTANCE.getInfo(texture).getMinFilter(), "window filter restored");
        } finally {
            TextureUtilHooks.clearSavedFilterTexture();
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glDeleteTextures(texture);
            source.discard();
            source.freeBuffers();
        }
    }

    private static int linkTrivialProgram() {
        final int vertex = GLStateManager.glCreateShader(GL20.GL_VERTEX_SHADER);
        GLStateManager.glShaderSource(vertex, "#version 330 core\nvoid main() { gl_Position = vec4(0.0); }\n");
        GLStateManager.glCompileShader(vertex);
        final int fragment = GLStateManager.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GLStateManager.glShaderSource(fragment, "#version 330 core\nout vec4 color;\nvoid main() { color = vec4(1.0); }\n");
        GLStateManager.glCompileShader(fragment);
        final int program = GLStateManager.glCreateProgram();
        GLStateManager.glAttachShader(program, vertex);
        GLStateManager.glAttachShader(program, fragment);
        GLStateManager.glLinkProgram(program);
        GLStateManager.glDeleteShader(vertex);
        GLStateManager.glDeleteShader(fragment);
        return program;
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

    private static int liveCull(TesrMaterial material) {
        return material.isNoCull() ? DrawState.DISABLED : DrawState.liveCull();
    }

    static RenderLayer capturedLayer(TesrMaterial material) {
        return Reflect.invoke(ModelPartBatcher.INSTANCE, "layerFor",
            new Class<?>[] { ResourceLocation.class, TesrMaterial.class, PassOverride.class, float.class, float.class, int.class, int.class, boolean.class, boolean.class },
            null, material, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, liveCull(material), DrawState.liveLit(material), DrawState.liveLightmap());
    }

    private static RenderLayer queuedLayer(TesrMaterial material) {
        return Reflect.invoke(TesrBatchRenderer.INSTANCE, "layerFor",
            new Class<?>[] { ResourceLocation.class, TesrMaterial.class, PassOverride.class, float.class, float.class, int.class, boolean.class, boolean.class },
            null, material, PassOverride.NONE, 0f, 0f, liveCull(material), DrawState.liveLit(material), DrawState.liveLightmap());
    }

    private static void drawInBatch(RenderLayer layer, boolean culled) {
        final int depth = GLStateManager.pushState(StateSet.BATCH);
        layer.startDrawing();
        assertEquals(culled, GL11.glIsEnabled(GL11.GL_CULL_FACE));
        layer.endDrawing();
        GLStateManager.popStateTo(depth);
    }

    @Test
    void queuedCullingSplitsBothLayerCachesAndRestoresTheDrawCaller() {
        for (TesrMaterial material : new TesrMaterial[] { EntityMaterials.DROPPED_ITEM_CUTOUT, EntityMaterials.GLINT }) {
            GLStateManager.disableCull();
            final RenderLayer twoSided = capturedLayer(material);
            GLStateManager.enableCull();
            final RenderLayer culled = capturedLayer(material);
            assertNotSame(twoSided, culled, "the last-layer shortcut must include culling");
            GLStateManager.disableCull();
            assertSame(twoSided, capturedLayer(material), "the layer map must retain both states");
        }

        GLStateManager.disableCull();
        final RenderLayer twoSided = capturedLayer(EntityMaterials.DROPPED_ITEM_CUTOUT);
        GLStateManager.enableCull();
        final RenderLayer culled = capturedLayer(EntityMaterials.DROPPED_ITEM_CUTOUT);
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
    }

    @Test
    void queuedTemplateCullingSurvivesCacheHitsAndRestoresTheCaller() {
        final TesrMaterial material = TesrMaterial.CURRENT_STATE;
        GLStateManager.disableCull();
        final RenderLayer twoSided = queuedLayer(material);
        GLStateManager.enableCull();
        final RenderLayer culled = queuedLayer(material);
        assertNotSame(twoSided, culled);
        GLStateManager.disableCull();
        assertSame(twoSided, queuedLayer(material));
        drawInBatch(culled, true);
        assertFalse(GL11.glIsEnabled(GL11.GL_CULL_FACE));
        GLStateManager.enableCull();
        drawInBatch(twoSided, false);
        assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE));

        final RenderLayer forcedTwoSided = queuedLayer(TesrMaterial.builder().noCull().build());
        drawInBatch(forcedTwoSided, false);
        assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE));
    }

    @Test
    void glintTintSlotsSplitLayers() {
        assertSame(glint(0), glint(0), "the same slot must reuse the layer");
        assertNotSame(glint(1), glint(0), "distinct slots must not share a layer");
        assertNotSame(glint(ShaderGlint.NO_TINT), glint(0), "an untinted glint must not share a tinted layer");
    }

    @Test
    void startDrawingLeavesUndeclaredPhasesToTheSurroundingContext() {
        layer(TesrMaterial.CURRENT_STATE).startDrawing();

        assertFalse(GLStateManager.getFogMode().isEnabled(), "fog");
    }

    @Test
    void endDrawingLeavesUndeclaredPhasesToTheSurroundingContext() {
        GLStateManager.enableFog();
        final RenderLayer layer = layer(TesrMaterial.CURRENT_STATE);
        layer.startDrawing();
        layer.endDrawing();

        assertTrue(GLStateManager.getFogMode().isEnabled(), "fog");
    }

    @Test
    void everyAxisAppliesAbsolutely() {
        GLStateManager.disableCull();
        GLStateManager.glCullFace(GL11.GL_FRONT);
        GLStateManager.disableLighting();
        GLStateManager.glColorMask(false, false, false, false);

        layer(TesrMaterial.CURRENT_STATE).startDrawing();

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
    }

    @Test
    void everyMaterialToggleApplies() {
        final TesrMaterial material = TesrMaterial.builder().noCull().unlit().noDepthWrite().cutout(0.5f).translucent().depthEqual().build();
        final RenderLayer layer = layer(material);
        layer.startDrawing();

        assertFalse(GLStateManager.getCullState().isEnabled(), "noCull");
        assertFalse(GLStateManager.getLightingState().isEnabled(), "unlit");
        assertFalse(GLStateManager.getDepthState().isEnabled(), "noDepthWrite");
        assertTrue(GLStateManager.getAlphaTest().isEnabled(), "cutout");
        assertEquals(0.5f, GLStateManager.getAlphaState().getReference(), "cutout threshold");
        assertTrue(GLStateManager.getBlendMode().isEnabled(), "translucent");
        assertEquals(GL11.GL_SRC_ALPHA, GLStateManager.getBlendState().getSrcRgb());
        assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, GLStateManager.getBlendState().getDstRgb());
        assertEquals(GL11.GL_EQUAL, GLStateManager.getDepthState().getFunc(), "depthEqual");

        layer.endDrawing();

        assertTrue(GLStateManager.getBlendMode().isEnabled(), "endDrawing must not reset blend");
        assertFalse(GLStateManager.getDepthState().isEnabled(), "endDrawing must not reset the depth mask");
        assertFalse(GLStateManager.getLightingState().isEnabled(), "endDrawing must not reset lighting");
        assertTrue(GLStateManager.getAlphaTest().isEnabled(), "endDrawing must not reset the alpha test");
    }

    @Test
    void effectiveAlphaStateSurvivesAPackOverridingTheRawAlphaTest() {
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
    }

    @Test
    void effectiveDepthMaskSurvivesAnotherPassHoldingItOff() {
        GLStateManager.getDepthState().setVanillaLayer(DepthColorStorage.DEPTH_LAYER);

        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        GLStateManager.enableBlend();
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);

        DepthColorStorage.disableDepthColor();

        assertSame(EntityMaterials.TRANSLUCENT_DEPTH_WRITE, EntityMaterials.fromCurrentState(true), "raw depth mask held off by another pass; the effective vanilla mask must pick the depth-write variant");
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
