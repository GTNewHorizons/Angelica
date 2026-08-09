package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.coderbot.iris.gl.program.ComputeProgram;
import net.coderbot.iris.uniforms.custom.CustomUniforms;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL42;

public final class PreRasterComputeDispatcher {
    public static final int LOC_UNRESOLVED = -2;

    private PreRasterComputeDispatcher() {}

    public static int dispatch(ComputeProgram compute, RwImageStoreExtractor.RwExtractMode mode, int cachedTargetSizeLoc, int width, int height, CustomUniforms uniforms) {
        final int prevProgram = GLStateManager.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        compute.use();
        uniforms.push(compute);
        int targetSizeLoc = cachedTargetSizeLoc;
        if (mode == RwImageStoreExtractor.RwExtractMode.COMPOSITE_FSH) {
            if (targetSizeLoc == LOC_UNRESOLVED) {
                targetSizeLoc = GLStateManager.glGetUniformLocation(compute.getProgramId(), "_vg_target_size");
            }
            if (targetSizeLoc >= 0) {
                GLStateManager.glUniform2i(targetSizeLoc, width, height);
            }
        }
        final int gx, gy, gz;
        switch (mode) {
            case COMPOSITE_VSH -> { gx = 1; gy = 1; gz = 1; }
            case COMPOSITE_FSH -> { gx = (width + 7) / 8; gy = (height + 7) / 8; gz = 1; }
            default -> { gx = 1; gy = 1; gz = 1; }
        }
        GLStateManager.glDispatchCompute(gx, gy, gz);
        RenderSystem.memoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        if (prevProgram != 0) GLStateManager.glUseProgram(prevProgram);
        return targetSizeLoc;
    }
}
