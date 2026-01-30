package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.gl.state.FogMode;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;
import net.coderbot.iris.gl.uniform.DynamicUniformHolder;
import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;

public class FogUniforms {
	private FogUniforms() {
		// no construction
	}

	public static void addFogUniforms(DynamicUniformHolder uniforms, FogMode fogMode) {
		if (fogMode == FogMode.OFF) {
			uniforms.uniform1i(UniformUpdateFrequency.ONCE, "fogMode", () -> 0);
			uniforms.uniform1i(UniformUpdateFrequency.ONCE, "fogShape", () -> -1);
		} else {
			uniforms.uniform1i("fogMode", () -> {
				if (!GLStateManager.getFogMode().isEnabled()) return 0;
				return GLStateManager.getFogState().getFogMode();
			}, ValueUpdateNotifier.NONE);
			uniforms.uniform1i("fogShape", () -> 0, ValueUpdateNotifier.NONE);
		}

		uniforms.uniform1f("fogDensity", () -> GLStateManager.getFogState().getDensity(), ValueUpdateNotifier.NONE);
		uniforms.uniform1f("fogStart", () -> GLStateManager.getFogState().getStart(), ValueUpdateNotifier.NONE);
		uniforms.uniform1f("fogEnd", () -> GLStateManager.getFogState().getEnd(), ValueUpdateNotifier.NONE);
	}
}
