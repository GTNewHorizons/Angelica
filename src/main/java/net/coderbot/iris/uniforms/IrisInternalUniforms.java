package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.gl.state.FogMode;
import net.coderbot.iris.gl.uniform.DynamicUniformHolder;
import org.joml.Vector3d;
import org.joml.Vector4f;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public class IrisInternalUniforms {
    private static final Vector4f FOG_COLOR = new Vector4f();

    private IrisInternalUniforms() {
    }

    public static void addFogUniforms(DynamicUniformHolder uniforms, FogMode fogMode) {
        uniforms.uniform4f(PER_FRAME, "iris_FogColor", () -> {
            final Vector3d color = GLStateManager.getFogState().getFogColor();
            return FOG_COLOR.set((float) color.x, (float) color.y, (float) color.z, GLStateManager.getFogState().getFogAlpha());
        });

        uniforms
            .uniform1f(PER_FRAME, "iris_FogStart", () -> GLStateManager.getFogState().getStart())
            .uniform1f(PER_FRAME, "iris_FogEnd", () -> GLStateManager.getFogState().getEnd())
            .uniform1f(PER_FRAME, "iris_FogDensity", () -> Math.max(0.0F, GLStateManager.getFogState().getDensity()));

        uniforms
            .uniform1f(PER_FRAME, "iris_currentAlphaTest", () -> GLStateManager.getAlphaState().getReference())
            .uniform1f(PER_FRAME, "alphaTestRef", () -> GLStateManager.getAlphaState().getReference());
    }
}
