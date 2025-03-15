package me.jellysquid.mods.sodium.client.gl.compat;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.managers.GLFogManager;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkFogMode;
import org.lwjgl.opengl.GL11;

public class FogHelper {
    private static final float FAR_PLANE_THRESHOLD_EXP = (float) Math.log(1.0f / 0.0019f);
    private static final float FAR_PLANE_THRESHOLD_EXP2 = (float) Math.sqrt(FAR_PLANE_THRESHOLD_EXP);

    public static float red, green, blue;

    public static float getFogEnd() {
    	return GLFogManager.getFogState().getEnd();
    }

    public static float getFogStart() {
    	return GLFogManager.getFogState().getStart();
    }

    public static float getFogDensity() {
    	return GLFogManager.getFogState().getDensity();
    }

    /**
     * Retrieves the current fog mode from the fixed-function pipeline.
     */
    public static ChunkFogMode getFogMode() {
        final int mode = GLFogManager.getFogState().getFogMode();

        if(mode == 0 || !GLFogManager.getFogMode().isEnabled())
        	return ChunkFogMode.NONE;

        return switch (mode) {
            case GL11.GL_EXP2, GL11.GL_EXP -> ChunkFogMode.EXP2;
            case GL11.GL_LINEAR -> ChunkFogMode.LINEAR;
            default -> throw new UnsupportedOperationException("Unknown fog mode: " + mode);
        };
    }

    public static float getFogCutoff() {
        return switch (GLFogManager.getFogState().getFogMode()) {
            case GL11.GL_LINEAR -> getFogEnd();
            case GL11.GL_EXP -> FAR_PLANE_THRESHOLD_EXP / getFogDensity();
            case GL11.GL_EXP2 -> FAR_PLANE_THRESHOLD_EXP2 / getFogDensity();
            default -> 0.0f;
        };
    }

    public static float[] getFogColor() {
        // TODO: Sodium
//    	return new float[]{BackgroundRenderer.red, BackgroundRenderer.green, BackgroundRenderer.blue, 1.0F};
    	return new float[]{red, green, blue, 1.0F};
    }
}
