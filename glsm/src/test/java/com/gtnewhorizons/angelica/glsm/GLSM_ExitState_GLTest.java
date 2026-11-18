package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredBlendHandler;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaBooleanLayer;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaStateLayer;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import net.minecraft.client.renderer.Tessellator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class GLSM_ExitState_GLTest {
    private final Matrix4f modelView = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f texture = new Matrix4f();
    private final Vector4f overlay = new Vector4f();
    private final Vector4f shaderColor = new Vector4f();
    private int outer;
    private int clientActiveUnit;
    private float lightmapX, lightmapY;
    private ImmediateExtendedAttribHandler extendedHandler;
    private DeferredBlendHandler blendHandler;

    @BeforeEach
    void setUp() {
        final GLContextState context = GLStateManager.ctx();
        modelView.set(context.modelViewMatrix);
        projection.set(context.projectionMatrix);
        texture.set(context.textures.getTextureUnitMatrix(0));
        overlay.set(context.overlayR, context.overlayG, context.overlayB, context.overlayA);
        shaderColor.set(context.shaderColorR, context.shaderColorG, context.shaderColorB, context.shaderColorA);
        lightmapX = context.lastBrightnessX;
        lightmapY = context.lastBrightnessY;
        extendedHandler = GLSMHooks.immediateExtendedHandler;
        blendHandler = GLSMHooks.blendHandler;
        clientActiveUnit = context.clientActiveTextureUnit;
        outer = GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS));
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.setTextureMatrix(0, new Matrix4f());
        GLSMCurrentAttribs.glColor4f(1, 1, 1, 1);
        GLSMLighting.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
        GLSMLighting.disableColorMaterial();
        GLSMRasterizer.enableCull();
        GLSMRasterizer.glCullFace(GL11.GL_BACK);
        GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLSMRasterizer.glPolygonOffset(2, 3);
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @AfterEach
    void tearDown() {
        GLStateManager.cancelExitCapture();
        GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0 + clientActiveUnit);
        GLSMHooks.blendHandler = blendHandler;
        GLSMHooks.immediateExtendedHandler = extendedHandler;
        GLSMBlend.getBlendMode().setVanillaLayer(null);
        GLSMBlend.getBlendState().setVanillaLayer(null);
        GLSMMatrices.setModelViewMatrix(modelView);
        GLSMMatrices.setProjectionMatrix(projection);
        GLSMMatrices.setTextureMatrix(0, texture);
        GLSMCurrentAttribs.setOverlayColor(overlay.x, overlay.y, overlay.z, overlay.w);
        GLSMCurrentAttribs.setShaderColor(shaderColor.x, shaderColor.y, shaderColor.z, shaderColor.w);
        GLSMCurrentAttribs.setLightmapTextureCoords(GL13.GL_TEXTURE1, lightmapX, lightmapY);
        GLSMAttribStack.popStateTo(outer);
    }

    @Test
    void unchangedWritesStillApplyAndLeaveOtherPolygonStateAlone() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLSMRasterizer.enableCull();
        GLSMRasterizer.glCullFace(GL11.GL_BACK);
        assertTrue(GLStateManager.finishExitCapture(state));

        GLSMRasterizer.disableCull();
        GLSMRasterizer.glCullFace(GL11.GL_FRONT);
        GLSMEnable.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GLSMRasterizer.glPolygonOffset(7, 11);
        assertTrue(GLStateManager.canApplyExitState(state));
        GLStateManager.applyExitState(state);
        assertTrue(GLSMRasterizer.getCullState().isEnabled());
        assertEquals(GL11.GL_BACK, GLSMRasterizer.getPolygonState().getCullFaceMode());
        assertTrue(GLSMRasterizer.getPolygonOffsetFillState().isEnabled());
        assertEquals(7, GLSMRasterizer.getPolygonState().getOffsetFactor());
        assertEquals(11, GLSMRasterizer.getPolygonState().getOffsetUnits());
    }

    @Test
    void attribPopUndoesTheWritesItRestores() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLSMEnable.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        assertEquals(ExitState.POLYGON_OFFSET_ENABLE_WRITTEN, GLStateManager.getExitPolygonOffsetWrites());
        GLSMAttribStack.glPushAttrib(GL11.GL_POLYGON_BIT | GL11.GL_CURRENT_BIT);
        GLSMRasterizer.glPolygonOffset(5, 6);
        GLSMRasterizer.disableCull();
        GLSMCurrentAttribs.glColor4f(0, 0, 0, 1);
        assertEquals(3, GLStateManager.getExitPolygonOffsetWrites());
        GLSMAttribStack.glPopAttrib();
        assertEquals(ExitState.POLYGON_OFFSET_ENABLE_WRITTEN, GLStateManager.getExitPolygonOffsetWrites());
        assertTrue(GLStateManager.finishExitCapture(state));

        GLSMRasterizer.disableCull();
        GLSMCurrentAttribs.glColor4f(0.25F, 0.5F, 0.75F, 1);
        GLSMRasterizer.glPolygonOffset(8, 9);
        GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.applyExitState(state);
        assertFalse(GLSMRasterizer.getCullState().isEnabled());
        assertEquals(0.25F, GLSMCurrentAttribs.getColor().getRed());
        assertEquals(8, GLSMRasterizer.getPolygonState().getOffsetFactor());
        assertTrue(GLSMRasterizer.getPolygonOffsetFillState().isEnabled());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void drawsDependOnTheAlphaFunctionOnlyWhenTheyReadItFromState(boolean materialFromState) {
        GLSMDepthStencilAlpha.enableAlphaTest();
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLSMCurrentAttribs.glColor4f(1, 1, 1, 1);
        GLStateManager.requireExitDrawState(materialFromState);
        assertTrue(GLStateManager.finishExitCapture(state));
        GLSMCurrentAttribs.glColor4f(0, 0, 0, 1);
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.6F);
        assertEquals(!materialFromState, GLStateManager.canApplyExitState(state));
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        assertTrue(GLStateManager.canApplyExitState(state));
        GLSMRasterizer.disableCull();
        assertFalse(GLStateManager.canApplyExitState(state));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void blendAndAlphaFunctionsMatterOnlyToDrawsWithThemEnabled(int mode) {
        GLSMBlend.disableBlend();
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMBlend.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLStateManager.requireExitDrawState(true);
        if (mode != 0) {
            if (mode == 2) {
                GLSMBlend.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
                GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            }
            GLSMBlend.enableBlend();
            GLSMDepthStencilAlpha.enableAlphaTest();
            GLStateManager.requireExitDrawState(true);
        }
        assertTrue(GLStateManager.finishExitCapture(state));
        GLSMBlend.disableBlend();
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        assertEquals(mode != 1, GLStateManager.canApplyExitState(state));
        GLSMBlend.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.5F);
        assertEquals(mode != 1, GLStateManager.canApplyExitState(state));
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        assertTrue(GLStateManager.canApplyExitState(state));
    }

    @Test
    void settingTheClientTextureUnitToItsCurrentValueIsStillRecorded() {
        GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLSMAttribStack.glPushAttrib(GL11.GL_TEXTURE_BIT);
        GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
        GLSMAttribStack.glPopAttrib();
        assertTrue(GLStateManager.finishExitCapture(state));
        GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE1);
        assertTrue(GLStateManager.canApplyExitState(state));
        GLStateManager.applyExitState(state);
        assertEquals(0, GLSMTextures.getClientActiveTextureUnit());

        GLStateManager.beginExitCapture(state);
        assertTrue(GLStateManager.finishExitCapture(state));
        GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.applyExitState(state);
        assertEquals(1, GLSMTextures.getClientActiveTextureUnit());
    }

    @Test
    void drawAfterAnAttribPopDependsOnTheOriginalCullState() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT);
        GLSMRasterizer.disableCull();
        GLStateManager.requireExitDrawState(false);
        GLSMAttribStack.glPopAttrib();
        GLStateManager.requireExitDrawState(false);
        assertTrue(GLStateManager.finishExitCapture(state));
        GLSMRasterizer.disableCull();
        assertFalse(GLStateManager.canApplyExitState(state));
    }

    @Test
    void lightingAndShaderStateAreNotDrawDependencies() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        assertFalse(GLStateManager.isExitLightingWritten());
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.enableLighting();
        assertTrue(GLStateManager.isExitLightingWritten());
        GLSMAttribStack.glPopAttrib();
        assertFalse(GLStateManager.isExitLightingWritten());
        GLStateManager.requireExitDrawState(false);
        assertTrue(GLStateManager.finishExitCapture(state));

        GLSMLighting.getLightingState().setEnabled(!GLSMLighting.getLightingState().isEnabled());
        GLSMLighting.getNormalizeState().setEnabled(!GLSMLighting.getNormalizeState().isEnabled());
        GLSMLighting.getRescaleNormalState().setEnabled(!GLSMLighting.getRescaleNormalState().isEnabled());
        GLSMLighting.getLightStates()[0].setEnabled(!GLSMLighting.getLightStates()[0].isEnabled());
        GLSMLighting.getLightStates()[1].setEnabled(!GLSMLighting.getLightStates()[1].isEnabled());
        GLSMRasterizer.glShadeModel(GLSMRasterizer.getShadeModelState().getValue() == GL11.GL_FLAT ? GL11.GL_SMOOTH : GL11.GL_FLAT);
        GLSMCurrentAttribs.setShaderColor(0.1F, 0.2F, 0.3F, 0.4F);
        GLSMCurrentAttribs.glNormal3f(0.5F, 0.25F, 0.75F);
        GLSMCurrentAttribs.glTexCoord2f(0.75F, 0.25F);
        assertTrue(GLStateManager.canApplyExitState(state));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void tessellatorDrawsDependOnlyOnTheCurrentValuesTheyFillIn(int mode) {
        final Tessellator tess = new Tessellator();
        tess.rawBuffer = new int[32];
        tess.vertexCount = 4;
        tess.drawMode = GL11.GL_QUADS;
        tess.hasNormals = mode != 1 && mode != 3;
        tess.hasTexture = mode != 2;
        tess.hasColor = tess.hasBrightness = true;
        GLSMHooks.immediateExtendedHandler = mode == 3 ? new FaceNormals() : null;
        GLSMCurrentAttribs.glNormal3f(0, 1, 0);
        GLSMCurrentAttribs.glTexCoord2f(0.25F, 0.5F);
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        TessellatorStreamingDrawer.fillMissingAttributes(tess);
        assertTrue(GLStateManager.finishExitCapture(state));
        if (mode == 1) assertEquals(0x7F00, tess.rawBuffer[6]);
        if (mode == 2) assertEquals(Float.floatToRawIntBits(0.25F), tess.rawBuffer[3]);
        if (mode == 3) assertEquals(0x7F, tess.rawBuffer[6]);
        GLSMCurrentAttribs.glNormal3f(1, 0, 0);
        GLSMCurrentAttribs.glTexCoord2f(0.75F, 0.5F);
        assertEquals(mode == 0 || mode == 3, GLStateManager.canApplyExitState(state));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deletingTheBoundTextureBlocksApplyingEvenIfTheCaptureUnboundIt(boolean explicitlyUnbound) {
        final int id = GLSMTextures.glGenTextures();
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, id);
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        if (explicitlyUnbound) GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        assertTrue(GLStateManager.finishExitCapture(state));
        assertTrue(GLStateManager.canApplyExitState(state));
        GLSMTextures.glDeleteTextures(id);
        assertFalse(GLStateManager.canApplyExitState(state));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void textureMatrixMustMatchOnlyWhenTheCaptureMultipliedOntoIt(boolean absolute) {
        GLSMMatrices.setTextureMatrix(0, new Matrix4f().translation(1, 2, 0));
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glPushMatrix();
        if (absolute) GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glTranslatef(3, 4, 0);
        GLStateManager.requireExitDrawState(false);
        GLSMMatrices.glPopMatrix();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        assertTrue(GLStateManager.finishExitCapture(state));
        final Matrix4f mainTexture = new Matrix4f().translation(8, 9, 0);
        GLSMMatrices.setTextureMatrix(0, mainTexture);
        assertEquals(absolute, GLStateManager.canApplyExitState(state));
        if (absolute) {
            GLStateManager.applyExitState(state);
            assertEquals(mainTexture, GLSMTextures.getTextures().getTextureUnitMatrix(0));
        }
    }

    @Test
    void changedOrUnbalancedViewMatricesReject() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLSMMatrices.glTranslatef(1, 0, 0);
        assertFalse(GLStateManager.finishExitCapture(state));
        GLSMMatrices.setModelViewMatrix(modelView);

        GLStateManager.beginExitCapture(state);
        GLSMMatrices.glPushMatrix();
        assertFalse(GLStateManager.finishExitCapture(state));
        GLSMMatrices.glPopMatrix();

        GLStateManager.beginExitCapture(state);
        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glTranslatef(2, 3, 4);
        GLSMMatrices.glPopMatrix();
        assertTrue(GLStateManager.finishExitCapture(state));

        GLStateManager.beginExitCapture(state);
        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glPopMatrix();
        assertFalse(GLStateManager.finishExitCapture(state));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void untrackedCallsAndStateQueriesReject(int operation) {
        final ExitState state = new ExitState();
        final int program = GLSMPrograms.getActiveProgram();
        GLStateManager.beginExitCapture(state);
        switch (operation) {
            case 0 -> GLSMEnable.glIsEnabled(GL11.GL_CULL_FACE);
            case 1 -> GLSMQueries.glGetInteger(GL11.GL_DEPTH_FUNC);
            case 2 -> GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);
            case 3 -> GLSMPrograms.glUseProgram(program);
            case 4 -> GLSMBlend.glBlendEquation(GL14.GL_FUNC_ADD);
            case 5 -> GLSMFog.fogColor((float) GLSMFog.getFogState().getFogColor().x,
                (float) GLSMFog.getFogState().getFogColor().y, (float) GLSMFog.getFogState().getFogColor().z,
                GLSMFog.getFogState().getFogAlpha());
            default -> throw new AssertionError();
        }
        assertFalse(GLStateManager.finishExitCapture(state));
    }

    @Test
    void suspendedCallsAreNeitherRecordedNorRejected() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        final boolean suspended = GLStateManager.suspendExitCapture();
        try {
            assertFalse(GLStateManager.isExitCaptureActive());
            GLSMEnable.glIsEnabled(GL11.GL_CULL_FACE);
            GLSMCurrentAttribs.glColor4f(0, 0, 0, 1);
            BatchStateGuard.suspend();
            try {
                GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);
            } finally {
                BatchStateGuard.resume();
            }
        } finally {
            GLStateManager.resumeExitCapture(suspended);
        }
        BatchStateGuard.suspend();
        try {
            assertFalse(GLStateManager.isExitCaptureActive());
            GLSMQueries.glGetInteger(GL11.GL_DEPTH_FUNC);
        } finally {
            BatchStateGuard.resume();
        }
        assertTrue(GLStateManager.isExitCaptureActive());
        assertTrue(GLStateManager.finishExitCapture(state));
        GLSMCurrentAttribs.glColor4f(0.5F, 1, 1, 1);
        GLStateManager.applyExitState(state);
        assertEquals(0.5F, GLSMCurrentAttribs.getColor().getRed());
    }

    @Test
    void attribPushedWhileSuspendedRestoresThatCapturesWrites() {
        final ExitState earlier = new ExitState();
        GLStateManager.beginExitCapture(earlier);
        GLSMCurrentAttribs.glColor4f(0.5F, 1, 1, 1);
        GLSMAttribStack.glPushAttrib(GL11.GL_CURRENT_BIT);
        GLSMAttribStack.glPopAttrib();
        assertTrue(GLStateManager.finishExitCapture(earlier));

        GLSMCurrentAttribs.glColor4f(0.25F, 1, 1, 1);
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        final boolean suspended = GLStateManager.suspendExitCapture();
        try {
            GLSMAttribStack.glPushAttrib(GL11.GL_CURRENT_BIT);
        } finally {
            GLStateManager.resumeExitCapture(suspended);
        }
        GLSMCurrentAttribs.glColor4f(0, 0, 0, 1);
        GLSMAttribStack.glPopAttrib();
        assertTrue(GLStateManager.finishExitCapture(state));

        GLSMCurrentAttribs.glColor4f(0.75F, 1, 1, 1);
        GLStateManager.applyExitState(state);
        assertEquals(0.75F, GLSMCurrentAttribs.getColor().getRed(), "the pop restored color, so the capture leaves it alone");
    }

    @Test
    void attribPoppedWhileSuspendedUndoesTheCapturesWrites() {
        GLSMCurrentAttribs.glColor4f(0.25F, 1, 1, 1);
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLSMAttribStack.glPushAttrib(GL11.GL_CURRENT_BIT);
        GLSMCurrentAttribs.glColor4f(0, 0, 0, 1);
        final boolean suspended = GLStateManager.suspendExitCapture();
        try {
            GLSMAttribStack.glPopAttrib();
        } finally {
            GLStateManager.resumeExitCapture(suspended);
        }
        assertTrue(GLStateManager.finishExitCapture(state));

        GLSMCurrentAttribs.glColor4f(0.75F, 1, 1, 1);
        GLStateManager.applyExitState(state);
        assertEquals(0.75F, GLSMCurrentAttribs.getColor().getRed(), "the pop restored color, so the capture leaves it alone");
    }

    @Test
    void separateAndReusedCapturesKeepOnlyTheirOwnWrites() {
        final ExitState first = new ExitState();
        final ExitState second = new ExitState();
        GLStateManager.beginExitCapture(first);
        GLSMCurrentAttribs.glColor4f(0.25F, 1, 1, 1);
        GLSMCurrentAttribs.setOverlayColor(0.1F, 0.2F, 0.3F, 0.4F);
        assertTrue(GLStateManager.finishExitCapture(first));
        GLStateManager.beginExitCapture(second);
        GLSMCurrentAttribs.glColor4f(0.75F, 1, 1, 1);
        assertTrue(GLStateManager.finishExitCapture(second));
        GLStateManager.applyExitState(first);
        assertEquals(0.25F, GLSMCurrentAttribs.getColor().getRed());
        assertEquals(0.4F, GLSMCurrentAttribs.getOverlayA());
        GLStateManager.applyExitState(second);
        assertEquals(0.75F, GLSMCurrentAttribs.getColor().getRed());
        first.clear();
        assertFalse(GLStateManager.canApplyExitState(first));
        GLStateManager.beginExitCapture(first);
        assertTrue(GLStateManager.finishExitCapture(first));
        GLSMCurrentAttribs.glColor4f(0.5F, 1, 1, 1);
        GLStateManager.applyExitState(first);
        assertEquals(0.5F, GLSMCurrentAttribs.getColor().getRed());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void toggledColorMaterialKeepsItsMaterialWritesUnlessAnAttribPopUndoesThem(boolean scoped) {
        GLSMLighting.enableColorMaterial();
        GLSMLighting.disableColorMaterial();
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLSMCurrentAttribs.glColor4f(1, 1, 1, 1);
        if (scoped) GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.enableColorMaterial();
        GLSMLighting.disableColorMaterial();
        if (scoped) GLSMAttribStack.glPopAttrib();
        assertTrue(GLStateManager.finishExitCapture(state));
        final var changed = BufferUtils.createFloatBuffer(4);
        changed.put(new float[]{0.2F, 0.3F, 0.4F, 1}).flip();
        GLSMLighting.glMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE, changed);
        assertTrue(GLStateManager.canApplyExitState(state));
        GLStateManager.applyExitState(state);
        assertEquals(scoped ? 0.2F : 1, GLSMLighting.getFrontMaterial().ambient.x);
        assertEquals(scoped ? 0.2F : 1, GLSMLighting.getBackMaterial().diffuse.x);
        assertFalse(GLSMLighting.getColorMaterial().isEnabled());
    }

    @Test
    void colorMaterialSettingsTheCaptureNeverTouchedAreLeftAlone() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLSMCurrentAttribs.glColor4f(1, 1, 1, 1);
        assertTrue(GLStateManager.finishExitCapture(state));
        GLSMLighting.glColorMaterial(GL11.GL_BACK, GL11.GL_EMISSION);
        GLSMLighting.enableColorMaterial();
        assertTrue(GLStateManager.canApplyExitState(state));
        GLStateManager.applyExitState(state);
        assertTrue(GLSMLighting.getColorMaterial().isEnabled());
        assertEquals(GL11.GL_BACK, GLSMLighting.getColorMaterialFace().getValue());
        assertEquals(GL11.GL_EMISSION, GLSMLighting.getColorMaterialParameter().getValue());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void colorMaterialFaceMustMatchOnlyWhenTheCaptureDidNotSetIt(boolean establishesContext) {
        GLSMLighting.glColorMaterial(GL11.GL_FRONT, GL11.GL_DIFFUSE);
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLSMCurrentAttribs.glColor4f(1, 1, 1, 1);
        if (establishesContext) GLSMLighting.glColorMaterial(GL11.GL_FRONT, GL11.GL_DIFFUSE);
        GLSMLighting.enableColorMaterial();
        GLSMLighting.disableColorMaterial();
        assertTrue(GLStateManager.finishExitCapture(state));
        GLSMLighting.glColorMaterial(GL11.GL_BACK, GL11.GL_EMISSION);
        assertEquals(establishesContext, GLStateManager.canApplyExitState(state));
    }

    @Test
    void writesUnderALockedIrisOverrideRecordAndApplyTheVanillaValues() {
        GLSMBlend.disableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        GLSMBlend.getBlendMode().beforeModify();
        GLSMBlend.getBlendState().beforeModify();
        final LockedBlend lock = new LockedBlend();
        GLSMHooks.blendHandler = lock;
        GLSMBlend.getBlendMode().setVanillaLayer(lock);
        GLSMBlend.getBlendState().setVanillaLayer(lock);
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLSMBlend.enableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        assertTrue(GLStateManager.finishExitCapture(state));
        lock.enabled = false;
        lock.blend.setAll(GL11.GL_DST_COLOR, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
        assertTrue(GLStateManager.canApplyExitState(state));
        GLStateManager.applyExitState(state);
        assertTrue(lock.enabled);
        assertEquals(GL11.GL_SRC_ALPHA, lock.blend.getSrcRgb());
        assertFalse(GLSMBlend.getBlendMode().isEnabled());
        assertEquals(GL11.GL_ONE, GLSMBlend.getBlendState().getSrcRgb());
        assertEquals(GL11.GL_ZERO, GLSMBlend.getBlendState().getDstRgb());
    }

    private static final class LockedBlend implements DeferredBlendHandler, VanillaBooleanLayer, VanillaStateLayer<BlendState> {
        boolean enabled = true;
        final BlendState blend = new BlendState(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        @Override public boolean isBlendLocked() { return true; }
        @Override public boolean isOverrideHeld() { return true; }
        @Override public void deferBlendModeToggle(boolean value) { enabled = value; }
        @Override public void deferBlendFunc(int sr, int dr, int sa, int da) { blend.setAll(sr, dr, sa, da); }
        @Override public void flushDeferredBlend() {}
        @Override public boolean getVanilla() { return enabled; }
        @Override public void setVanilla(boolean value) { enabled = value; }
        @Override public void readVanilla(BlendState into) { into.set(blend); }
        @Override public void writeVanilla(BlendState from) { blend.set(from); }
    }

    private static final class FaceNormals implements ImmediateExtendedAttribHandler {
        @Override public boolean wantsExtended() { return false; }
        @Override public boolean wantsTangent() { return false; }
        @Override public boolean wantsEntityIds() { return false; }
        @Override public boolean wantsExtendedCapture() { return false; }
        @Override public boolean wantsFaceNormals() { return true; }
        @Override public void writeFaceNormals(int[] data, int count) {
            for (int i = 0; i < count; i++) data[i * 8 + 6] = 0x7F;
        }
        @Override public void build(int[] data, int count, int primitive, int normal, long destination, int stride) {}
        @Override public void buildPacked(long source, int stride, int position, int texture, int normal,
            int count, int primitive, long destination, int destinationStride) {}
    }
}
