package me.jellysquid.mods.sodium.client.gl.compat;

import com.mojang.blaze3d.platform.GlStateManager;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkFogMode;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL20;

public class FogHelper {
    private static final float FAR_PLANE_THRESHOLD_EXP = (float) Math.log(1.0f / 0.0019f);
    private static final float FAR_PLANE_THRESHOLD_EXP2 = MathHelper.sqrt(FAR_PLANE_THRESHOLD_EXP);

    public static float getFogEnd() {
    	return GlStateManager.FOG.end;
    }

    public static float getFogStart() {
    	return GlStateManager.FOG.start;
    }

    public static float getFogDensity() {
    	return GlStateManager.FOG.density;
    }

    /**
     * Retrieves the current fog mode from the fixed-function pipeline.
     */
    public static ChunkFogMode getFogMode() {
        int mode = GlStateManager.FOG.mode;

        if(mode == 0 || !GlStateManager.FOG.capState.state)
        	return ChunkFogMode.NONE;

        switch (mode) {
            case GL20.GL_EXP2:
            case GL20.GL_EXP:
                return ChunkFogMode.EXP2;
            case GL20.GL_LINEAR:
                return ChunkFogMode.LINEAR;
            default:
                throw new UnsupportedOperationException("Unknown fog mode: " + mode);
        }
    }

    public static float getFogCutoff() {
    	int mode = GlStateManager.FOG.mode;

        switch (mode) {
            case GL20.GL_LINEAR:
                return getFogEnd();
            case GL20.GL_EXP:
                return FAR_PLANE_THRESHOLD_EXP / getFogDensity();
            case GL20.GL_EXP2:
                return FAR_PLANE_THRESHOLD_EXP2 / getFogDensity();
            default:
                return 0.0f;
        }
    }

    public static float[] getFogColor() {
    	return new float[]{BackgroundRenderer.red, BackgroundRenderer.green, BackgroundRenderer.blue, 1.0F};
    }
}
