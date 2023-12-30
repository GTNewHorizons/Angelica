package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.gl.uniform.DynamicUniformHolder;

public class FogUniforms {
	private FogUniforms() {
		// no construction
	}

	public static void addFogUniforms(DynamicUniformHolder uniforms) {
		uniforms.uniform1i("fogMode", () -> {
            if(!GLStateManager.getFogMode().isEnabled())  return 0;

            return GLStateManager.getFogState().getFogMode();
		}, listener -> {
			StateUpdateNotifiers.fogToggleNotifier.setListener(listener);
			StateUpdateNotifiers.fogModeNotifier.setListener(listener);
		});

		uniforms.uniform1f("fogDensity", () -> GLStateManager.getFogState().getDensity(), listener -> {
			StateUpdateNotifiers.fogToggleNotifier.setListener(listener);
			StateUpdateNotifiers.fogDensityNotifier.setListener(listener);
		});

		uniforms.uniform1f("fogStart", () -> GLStateManager.getFogState().getStart(), listener -> {
			StateUpdateNotifiers.fogToggleNotifier.setListener(listener);
			StateUpdateNotifiers.fogStartNotifier.setListener(listener);
		});

		uniforms.uniform1f("fogEnd", () -> GLStateManager.getFogState().getEnd(), listener -> {
			StateUpdateNotifiers.fogToggleNotifier.setListener(listener);
			StateUpdateNotifiers.fogEndNotifier.setListener(listener);
		});
	}
}
