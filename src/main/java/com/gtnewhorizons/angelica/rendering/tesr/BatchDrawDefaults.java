package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.MatrixHelper;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.TextureUtilHooks;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.layer.PassOverride;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;

public final class BatchDrawDefaults {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static int passFramebuffer = -1;

    private final Matrix4f callerTextureMatrix = new Matrix4f();
    private int callerFramebuffer = -1;
    private int callerProgram;
    private boolean textureMatrixReset;
    private boolean filterPaused;
    private boolean overridesCleared;

    public static void capturePassFramebuffer() {
        passFramebuffer = GLStateManager.getDrawFramebuffer();
    }

    public static boolean drawsToPassFramebuffer() {
        return passFramebuffer < 0 || TesrBatchRenderer.deferredPipeline() != null
            || GLStateManager.getDrawFramebuffer() == passFramebuffer;
    }

    public void apply() {
        if (passFramebuffer >= 0 && TesrBatchRenderer.deferredPipeline() == null) {
            final int framebuffer = GLStateManager.getDrawFramebuffer();
            if (framebuffer != passFramebuffer) {
                callerFramebuffer = framebuffer;
                GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, passFramebuffer);
            }
        }
        final int program = GLStateManager.getActiveProgram();
        if (program != 0 && !Program.isManagedBind(program) && !DepthColorStorage.isOwnedProgram(program)) {
            callerProgram = program;
            GLStateManager.glUseProgram(0);
        }
        final Matrix4f textureMatrix = GLStateManager.getTextures().getTextureUnitMatrix(0);
        if (!MatrixHelper.isIdentity(textureMatrix)) {
            callerTextureMatrix.set(textureMatrix);
            GLStateManager.setTextureMatrix(0, IDENTITY);
            textureMatrixReset = true;
        }
        filterPaused = TextureUtilHooks.pauseTemporaryFilter();
        if (GbufferPrograms.getSpecialCondition() != null || GbufferPrograms.getDeclaredTranslucency() != null
            || GbufferPrograms.getOverridePhase() != null) {
            PassOverride.NONE.apply();
            overridesCleared = true;
        }
    }

    public void restore() {
        if (overridesCleared) {
            overridesCleared = false;
            PassOverride.NONE.clear();
        }
        if (filterPaused) {
            filterPaused = false;
            TextureUtilHooks.resumeTemporaryFilter();
        }
        if (textureMatrixReset) {
            textureMatrixReset = false;
            GLStateManager.setTextureMatrix(0, callerTextureMatrix);
        }
        if (callerProgram != 0) {
            final int program = callerProgram;
            callerProgram = 0;
            GLStateManager.glUseProgram(program);
        }
        if (callerFramebuffer >= 0) {
            final int framebuffer = callerFramebuffer;
            callerFramebuffer = -1;
            GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        }
    }
}
