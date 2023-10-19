package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.client.Shaders;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.gl.uniform.DynamicUniformHolder;

public class FogUniforms {
	private FogUniforms() {
		// no construction
	}

	public static void addFogUniforms(DynamicUniformHolder uniforms) {
		uniforms.uniform1i("fogMode", () -> {
            if(!Shaders.isFogEnabled())  return 0;

            return Shaders.fogMode;

//			GlStateManager.FogState fog = GlStateManagerAccessor.getFOG();
//			if (!((BooleanStateAccessor) fog.enable).isEnabled()) { return 0; }
//			return GlStateManagerAccessor.getFOG().mode;
		}, listener -> {
			StateUpdateNotifiers.fogToggleNotifier.setListener(listener);
			StateUpdateNotifiers.fogModeNotifier.setListener(listener);
		});

		uniforms.uniform1f("fogDensity", () -> Shaders.fogDensity/*GlStateManagerAccessor.getFOG().density*/, listener -> {
			StateUpdateNotifiers.fogToggleNotifier.setListener(listener);
			StateUpdateNotifiers.fogDensityNotifier.setListener(listener);
		});

		uniforms.uniform1f("fogStart", () -> Shaders.fogStart/*GlStateManagerAccessor.getFOG().start*/, listener -> {
			StateUpdateNotifiers.fogToggleNotifier.setListener(listener);
			StateUpdateNotifiers.fogStartNotifier.setListener(listener);
		});

		uniforms.uniform1f("fogEnd", () -> Shaders.fogEnd/*GlStateManagerAccessor.getFOG().end*/, listener -> {
			StateUpdateNotifiers.fogToggleNotifier.setListener(listener);
			StateUpdateNotifiers.fogEndNotifier.setListener(listener);
		});
	}
}
