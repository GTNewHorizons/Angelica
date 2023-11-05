package me.jellysquid.mods.sodium.client.gl.compat;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkFogMode;
import org.lwjgl.opengl.GL11;

public class FogHelper {
    private static final float FAR_PLANE_THRESHOLD_EXP = (float) Math.log(1.0f / 0.0019f);
    private static final float FAR_PLANE_THRESHOLD_EXP2 = (float) Math.sqrt(FAR_PLANE_THRESHOLD_EXP);

    public static float getFogEnd() {
    	return GLStateManager.getFog().end;
    }

    public static float getFogStart() {
    	return GLStateManager.getFog().start;
    }

    public static float getFogDensity() {
    	return GLStateManager.getFog().density;
    }

    /**
     * Retrieves the current fog mode from the fixed-function pipeline.
     */
    public static ChunkFogMode getFogMode() {
        int mode = GLStateManager.getFog().fogMode;

        if(mode == 0 || !GLStateManager.getFog().mode.isEnabled())
        	return ChunkFogMode.NONE;

        return switch (mode) {
            case GL11.GL_EXP2, GL11.GL_EXP -> ChunkFogMode.EXP2;
            case GL11.GL_LINEAR -> ChunkFogMode.LINEAR;
            default -> throw new UnsupportedOperationException("Unknown fog mode: " + mode);
        };
    }

    public static float getFogCutoff() {
    	int mode = GLStateManager.getFog().fogMode;

        return switch (mode) {
            case GL11.GL_LINEAR -> getFogEnd();
            case GL11.GL_EXP -> FAR_PLANE_THRESHOLD_EXP / getFogDensity();
            case GL11.GL_EXP2 -> FAR_PLANE_THRESHOLD_EXP2 / getFogDensity();
            default -> 0.0f;
        };
    }

    public static float[] getFogColor() {
        // TODO: Sodium
//    	return new float[]{BackgroundRenderer.red, BackgroundRenderer.green, BackgroundRenderer.blue, 1.0F};
    	return new float[]{0.0F, 0.0F, 0.0F, 1.0F};
    }
}
