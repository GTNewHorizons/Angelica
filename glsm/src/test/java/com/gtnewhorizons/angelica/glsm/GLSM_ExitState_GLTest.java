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
        outer = GLStateManager.pushState(StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS));
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.setTextureMatrix(0, new Matrix4f());
        GLStateManager.glColor4f(1, 1, 1, 1);
        GLStateManager.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
        GLStateManager.disableColorMaterial();
        GLStateManager.enableCull();
        GLStateManager.glCullFace(GL11.GL_BACK);
        GLStateManager.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glPolygonOffset(2, 3);
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @AfterEach
    void tearDown() {
        GLStateManager.cancelExitCapture();
        GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0 + clientActiveUnit);
        GLSMHooks.blendHandler = blendHandler;
        GLSMHooks.immediateExtendedHandler = extendedHandler;
        GLStateManager.getBlendMode().setVanillaLayer(null);
        GLStateManager.getBlendState().setVanillaLayer(null);
        GLStateManager.setModelViewMatrix(modelView);
        GLStateManager.setProjectionMatrix(projection);
        GLStateManager.setTextureMatrix(0, texture);
        GLStateManager.setOverlayColor(overlay.x, overlay.y, overlay.z, overlay.w);
        GLStateManager.setShaderColor(shaderColor.x, shaderColor.y, shaderColor.z, shaderColor.w);
        GLStateManager.setLightmapTextureCoords(GL13.GL_TEXTURE1, lightmapX, lightmapY);
        GLStateManager.popStateTo(outer);
    }

    @Test
    void unchangedWritesStillApplyAndLeaveOtherPolygonStateAlone() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLStateManager.enableCull();
        GLStateManager.glCullFace(GL11.GL_BACK);
        assertTrue(GLStateManager.finishExitCapture(state));

        GLStateManager.disableCull();
        GLStateManager.glCullFace(GL11.GL_FRONT);
        GLStateManager.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glPolygonOffset(7, 11);
        assertTrue(GLStateManager.canApplyExitState(state));
        GLStateManager.applyExitState(state);
        assertTrue(GLStateManager.getCullState().isEnabled());
        assertEquals(GL11.GL_BACK, GLStateManager.getPolygonState().getCullFaceMode());
        assertTrue(GLStateManager.getPolygonOffsetFillState().isEnabled());
        assertEquals(7, GLStateManager.getPolygonState().getOffsetFactor());
        assertEquals(11, GLStateManager.getPolygonState().getOffsetUnits());
    }

    @Test
    void attribPopUndoesTheWritesItRestores() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLStateManager.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        assertEquals(ExitState.POLYGON_OFFSET_ENABLE_WRITTEN, GLStateManager.getExitPolygonOffsetWrites());
        GLStateManager.glPushAttrib(GL11.GL_POLYGON_BIT | GL11.GL_CURRENT_BIT);
        GLStateManager.glPolygonOffset(5, 6);
        GLStateManager.disableCull();
        GLStateManager.glColor4f(0, 0, 0, 1);
        assertEquals(3, GLStateManager.getExitPolygonOffsetWrites());
        GLStateManager.glPopAttrib();
        assertEquals(ExitState.POLYGON_OFFSET_ENABLE_WRITTEN, GLStateManager.getExitPolygonOffsetWrites());
        assertTrue(GLStateManager.finishExitCapture(state));

        GLStateManager.disableCull();
        GLStateManager.glColor4f(0.25F, 0.5F, 0.75F, 1);
        GLStateManager.glPolygonOffset(8, 9);
        GLStateManager.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.applyExitState(state);
        assertFalse(GLStateManager.getCullState().isEnabled());
        assertEquals(0.25F, GLStateManager.getColor().getRed());
        assertEquals(8, GLStateManager.getPolygonState().getOffsetFactor());
        assertTrue(GLStateManager.getPolygonOffsetFillState().isEnabled());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void drawsDependOnTheAlphaFunctionOnlyWhenTheyReadItFromState(boolean materialFromState) {
        GLStateManager.enableAlphaTest();
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLStateManager.glColor4f(1, 1, 1, 1);
        GLStateManager.requireExitDrawState(materialFromState);
        assertTrue(GLStateManager.finishExitCapture(state));
        GLStateManager.glColor4f(0, 0, 0, 1);
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.6F);
        assertEquals(!materialFromState, GLStateManager.canApplyExitState(state));
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        assertTrue(GLStateManager.canApplyExitState(state));
        GLStateManager.disableCull();
        assertFalse(GLStateManager.canApplyExitState(state));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void blendAndAlphaFunctionsMatterOnlyToDrawsWithThemEnabled(int mode) {
        GLStateManager.disableBlend();
        GLStateManager.disableAlphaTest();
        GLStateManager.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLStateManager.requireExitDrawState(true);
        if (mode != 0) {
            if (mode == 2) {
                GLStateManager.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
                GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            }
            GLStateManager.enableBlend();
            GLStateManager.enableAlphaTest();
            GLStateManager.requireExitDrawState(true);
        }
        assertTrue(GLStateManager.finishExitCapture(state));
        GLStateManager.disableBlend();
        GLStateManager.disableAlphaTest();
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        assertEquals(mode != 1, GLStateManager.canApplyExitState(state));
        GLStateManager.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5F);
        assertEquals(mode != 1, GLStateManager.canApplyExitState(state));
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        assertTrue(GLStateManager.canApplyExitState(state));
    }

    @Test
    void settingTheClientTextureUnitToItsCurrentValueIsStillRecorded() {
        GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLStateManager.glPushAttrib(GL11.GL_TEXTURE_BIT);
        GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glPopAttrib();
        assertTrue(GLStateManager.finishExitCapture(state));
        GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
        assertTrue(GLStateManager.canApplyExitState(state));
        GLStateManager.applyExitState(state);
        assertEquals(0, GLStateManager.getClientActiveTextureUnit());

        GLStateManager.beginExitCapture(state);
        assertTrue(GLStateManager.finishExitCapture(state));
        GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.applyExitState(state);
        assertEquals(1, GLStateManager.getClientActiveTextureUnit());
    }

    @Test
    void drawAfterAnAttribPopDependsOnTheOriginalCullState() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT);
        GLStateManager.disableCull();
        GLStateManager.requireExitDrawState(false);
        GLStateManager.glPopAttrib();
        GLStateManager.requireExitDrawState(false);
        assertTrue(GLStateManager.finishExitCapture(state));
        GLStateManager.disableCull();
        assertFalse(GLStateManager.canApplyExitState(state));
    }

    @Test
    void lightingAndShaderStateAreNotDrawDependencies() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        assertFalse(GLStateManager.isExitLightingWritten());
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLStateManager.enableLighting();
        assertTrue(GLStateManager.isExitLightingWritten());
        GLStateManager.glPopAttrib();
        assertFalse(GLStateManager.isExitLightingWritten());
        GLStateManager.requireExitDrawState(false);
        assertTrue(GLStateManager.finishExitCapture(state));

        GLStateManager.getLightingState().setEnabled(!GLStateManager.getLightingState().isEnabled());
        GLStateManager.getNormalizeState().setEnabled(!GLStateManager.getNormalizeState().isEnabled());
        GLStateManager.getRescaleNormalState().setEnabled(!GLStateManager.getRescaleNormalState().isEnabled());
        GLStateManager.getLightStates()[0].setEnabled(!GLStateManager.getLightStates()[0].isEnabled());
        GLStateManager.getLightStates()[1].setEnabled(!GLStateManager.getLightStates()[1].isEnabled());
        GLStateManager.glShadeModel(GLStateManager.getShadeModelState().getValue() == GL11.GL_FLAT ? GL11.GL_SMOOTH : GL11.GL_FLAT);
        GLStateManager.setShaderColor(0.1F, 0.2F, 0.3F, 0.4F);
        GLStateManager.glNormal3f(0.5F, 0.25F, 0.75F);
        GLStateManager.glTexCoord2f(0.75F, 0.25F);
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
        GLStateManager.glNormal3f(0, 1, 0);
        GLStateManager.glTexCoord2f(0.25F, 0.5F);
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        TessellatorStreamingDrawer.fillMissingAttributes(tess);
        assertTrue(GLStateManager.finishExitCapture(state));
        if (mode == 1) assertEquals(0x7F00, tess.rawBuffer[6]);
        if (mode == 2) assertEquals(Float.floatToRawIntBits(0.25F), tess.rawBuffer[3]);
        if (mode == 3) assertEquals(0x7F, tess.rawBuffer[6]);
        GLStateManager.glNormal3f(1, 0, 0);
        GLStateManager.glTexCoord2f(0.75F, 0.5F);
        assertEquals(mode == 0 || mode == 3, GLStateManager.canApplyExitState(state));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deletingTheBoundTextureBlocksApplyingEvenIfTheCaptureUnboundIt(boolean explicitlyUnbound) {
        final int id = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        if (explicitlyUnbound) GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        assertTrue(GLStateManager.finishExitCapture(state));
        assertTrue(GLStateManager.canApplyExitState(state));
        GLStateManager.glDeleteTextures(id);
        assertFalse(GLStateManager.canApplyExitState(state));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void textureMatrixMustMatchOnlyWhenTheCaptureMultipliedOntoIt(boolean absolute) {
        GLStateManager.setTextureMatrix(0, new Matrix4f().translation(1, 2, 0));
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glPushMatrix();
        if (absolute) GLStateManager.glLoadIdentity();
        GLStateManager.glTranslatef(3, 4, 0);
        GLStateManager.requireExitDrawState(false);
        GLStateManager.glPopMatrix();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        assertTrue(GLStateManager.finishExitCapture(state));
        final Matrix4f mainTexture = new Matrix4f().translation(8, 9, 0);
        GLStateManager.setTextureMatrix(0, mainTexture);
        assertEquals(absolute, GLStateManager.canApplyExitState(state));
        if (absolute) {
            GLStateManager.applyExitState(state);
            assertEquals(mainTexture, GLStateManager.getTextures().getTextureUnitMatrix(0));
        }
    }

    @Test
    void changedOrUnbalancedViewMatricesReject() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLStateManager.glTranslatef(1, 0, 0);
        assertFalse(GLStateManager.finishExitCapture(state));
        GLStateManager.setModelViewMatrix(modelView);

        GLStateManager.beginExitCapture(state);
        GLStateManager.glPushMatrix();
        assertFalse(GLStateManager.finishExitCapture(state));
        GLStateManager.glPopMatrix();

        GLStateManager.beginExitCapture(state);
        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(2, 3, 4);
        GLStateManager.glPopMatrix();
        assertTrue(GLStateManager.finishExitCapture(state));

        GLStateManager.beginExitCapture(state);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glPopMatrix();
        assertFalse(GLStateManager.finishExitCapture(state));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void untrackedCallsAndStateQueriesReject(int operation) {
        final ExitState state = new ExitState();
        final int program = GLStateManager.getActiveProgram();
        GLStateManager.beginExitCapture(state);
        switch (operation) {
            case 0 -> GLStateManager.glIsEnabled(GL11.GL_CULL_FACE);
            case 1 -> GLStateManager.glGetInteger(GL11.GL_DEPTH_FUNC);
            case 2 -> GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
            case 3 -> GLStateManager.glUseProgram(program);
            case 4 -> GLStateManager.glBlendEquation(GL14.GL_FUNC_ADD);
            case 5 -> GLStateManager.fogColor((float) GLStateManager.getFogState().getFogColor().x,
                (float) GLStateManager.getFogState().getFogColor().y, (float) GLStateManager.getFogState().getFogColor().z,
                GLStateManager.getFogState().getFogAlpha());
            default -> throw new AssertionError();
        }
        assertFalse(GLStateManager.finishExitCapture(state));
    }

    @Test
    void suspendedCallsAreNeitherRecordedNorRejected() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLStateManager.suspendExitCapture();
        try {
            assertFalse(GLStateManager.isExitCaptureActive());
            GLStateManager.glIsEnabled(GL11.GL_CULL_FACE);
            GLStateManager.glColor4f(0, 0, 0, 1);
            BatchStateGuard.suspend();
            try {
                GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
            } finally {
                BatchStateGuard.resume();
            }
        } finally {
            GLStateManager.resumeExitCapture();
        }
        BatchStateGuard.suspend();
        try {
            assertFalse(GLStateManager.isExitCaptureActive());
            GLStateManager.glGetInteger(GL11.GL_DEPTH_FUNC);
        } finally {
            BatchStateGuard.resume();
        }
        assertTrue(GLStateManager.isExitCaptureActive());
        assertTrue(GLStateManager.finishExitCapture(state));
        GLStateManager.glColor4f(0.5F, 1, 1, 1);
        GLStateManager.applyExitState(state);
        assertEquals(0.5F, GLStateManager.getColor().getRed());
    }

    @Test
    void attribPushedWhileSuspendedRestoresThatCapturesWrites() {
        final ExitState earlier = new ExitState();
        GLStateManager.beginExitCapture(earlier);
        GLStateManager.glColor4f(0.5F, 1, 1, 1);
        GLStateManager.glPushAttrib(GL11.GL_CURRENT_BIT);
        GLStateManager.glPopAttrib();
        assertTrue(GLStateManager.finishExitCapture(earlier));

        GLStateManager.glColor4f(0.25F, 1, 1, 1);
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLStateManager.suspendExitCapture();
        try {
            GLStateManager.glPushAttrib(GL11.GL_CURRENT_BIT);
        } finally {
            GLStateManager.resumeExitCapture();
        }
        GLStateManager.glColor4f(0, 0, 0, 1);
        GLStateManager.glPopAttrib();
        assertTrue(GLStateManager.finishExitCapture(state));

        GLStateManager.glColor4f(0.75F, 1, 1, 1);
        GLStateManager.applyExitState(state);
        assertEquals(0.75F, GLStateManager.getColor().getRed(), "the pop restored color, so the capture leaves it alone");
    }

    @Test
    void attribPoppedWhileSuspendedUndoesTheCapturesWrites() {
        GLStateManager.glColor4f(0.25F, 1, 1, 1);
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLStateManager.glPushAttrib(GL11.GL_CURRENT_BIT);
        GLStateManager.glColor4f(0, 0, 0, 1);
        GLStateManager.suspendExitCapture();
        try {
            GLStateManager.glPopAttrib();
        } finally {
            GLStateManager.resumeExitCapture();
        }
        assertTrue(GLStateManager.finishExitCapture(state));

        GLStateManager.glColor4f(0.75F, 1, 1, 1);
        GLStateManager.applyExitState(state);
        assertEquals(0.75F, GLStateManager.getColor().getRed(), "the pop restored color, so the capture leaves it alone");
    }

    @Test
    void separateAndReusedCapturesKeepOnlyTheirOwnWrites() {
        final ExitState first = new ExitState();
        final ExitState second = new ExitState();
        GLStateManager.beginExitCapture(first);
        GLStateManager.glColor4f(0.25F, 1, 1, 1);
        GLStateManager.setOverlayColor(0.1F, 0.2F, 0.3F, 0.4F);
        assertTrue(GLStateManager.finishExitCapture(first));
        GLStateManager.beginExitCapture(second);
        GLStateManager.glColor4f(0.75F, 1, 1, 1);
        assertTrue(GLStateManager.finishExitCapture(second));
        GLStateManager.applyExitState(first);
        assertEquals(0.25F, GLStateManager.getColor().getRed());
        assertEquals(0.4F, GLStateManager.getOverlayA());
        GLStateManager.applyExitState(second);
        assertEquals(0.75F, GLStateManager.getColor().getRed());
        first.clear();
        assertFalse(GLStateManager.canApplyExitState(first));
        GLStateManager.beginExitCapture(first);
        assertTrue(GLStateManager.finishExitCapture(first));
        GLStateManager.glColor4f(0.5F, 1, 1, 1);
        GLStateManager.applyExitState(first);
        assertEquals(0.5F, GLStateManager.getColor().getRed());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void toggledColorMaterialKeepsItsMaterialWritesUnlessAnAttribPopUndoesThem(boolean scoped) {
        GLStateManager.enableColorMaterial();
        GLStateManager.disableColorMaterial();
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLStateManager.glColor4f(1, 1, 1, 1);
        if (scoped) GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLStateManager.enableColorMaterial();
        GLStateManager.disableColorMaterial();
        if (scoped) GLStateManager.glPopAttrib();
        assertTrue(GLStateManager.finishExitCapture(state));
        final var changed = BufferUtils.createFloatBuffer(4);
        changed.put(new float[]{0.2F, 0.3F, 0.4F, 1}).flip();
        GLStateManager.glMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE, changed);
        assertTrue(GLStateManager.canApplyExitState(state));
        GLStateManager.applyExitState(state);
        assertEquals(scoped ? 0.2F : 1, GLStateManager.getFrontMaterial().ambient.x);
        assertEquals(scoped ? 0.2F : 1, GLStateManager.getBackMaterial().diffuse.x);
        assertFalse(GLStateManager.getColorMaterial().isEnabled());
    }

    @Test
    void colorMaterialSettingsTheCaptureNeverTouchedAreLeftAlone() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLStateManager.glColor4f(1, 1, 1, 1);
        assertTrue(GLStateManager.finishExitCapture(state));
        GLStateManager.glColorMaterial(GL11.GL_BACK, GL11.GL_EMISSION);
        GLStateManager.enableColorMaterial();
        assertTrue(GLStateManager.canApplyExitState(state));
        GLStateManager.applyExitState(state);
        assertTrue(GLStateManager.getColorMaterial().isEnabled());
        assertEquals(GL11.GL_BACK, GLStateManager.getColorMaterialFace().getValue());
        assertEquals(GL11.GL_EMISSION, GLStateManager.getColorMaterialParameter().getValue());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void colorMaterialFaceMustMatchOnlyWhenTheCaptureDidNotSetIt(boolean establishesContext) {
        GLStateManager.glColorMaterial(GL11.GL_FRONT, GL11.GL_DIFFUSE);
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLStateManager.glColor4f(1, 1, 1, 1);
        if (establishesContext) GLStateManager.glColorMaterial(GL11.GL_FRONT, GL11.GL_DIFFUSE);
        GLStateManager.enableColorMaterial();
        GLStateManager.disableColorMaterial();
        assertTrue(GLStateManager.finishExitCapture(state));
        GLStateManager.glColorMaterial(GL11.GL_BACK, GL11.GL_EMISSION);
        assertEquals(establishesContext, GLStateManager.canApplyExitState(state));
    }

    @Test
    void writesUnderALockedIrisOverrideRecordAndApplyTheVanillaValues() {
        GLStateManager.disableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.getBlendMode().beforeModify();
        GLStateManager.getBlendState().beforeModify();
        final LockedBlend lock = new LockedBlend();
        GLSMHooks.blendHandler = lock;
        GLStateManager.getBlendMode().setVanillaLayer(lock);
        GLStateManager.getBlendState().setVanillaLayer(lock);
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        assertTrue(GLStateManager.finishExitCapture(state));
        lock.enabled = false;
        lock.blend.setAll(GL11.GL_DST_COLOR, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
        assertTrue(GLStateManager.canApplyExitState(state));
        GLStateManager.applyExitState(state);
        assertTrue(lock.enabled);
        assertEquals(GL11.GL_SRC_ALPHA, lock.blend.getSrcRgb());
        assertFalse(GLStateManager.getBlendMode().isEnabled());
        assertEquals(GL11.GL_ONE, GLStateManager.getBlendState().getSrcRgb());
        assertEquals(GL11.GL_ZERO, GLStateManager.getBlendState().getDstRgb());
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
