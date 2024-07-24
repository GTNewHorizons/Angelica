package net.coderbot.iris.samplers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gl.sampler.SamplerHolder;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.rendertarget.RenderTarget;
import net.coderbot.iris.rendertarget.RenderTargets;
import net.coderbot.iris.shaderpack.PackRenderTargetDirectives;
import net.coderbot.iris.shadows.ShadowRenderTargets;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.AbstractTexture;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class IrisSamplers {
	public static final int ALBEDO_TEXTURE_UNIT = 0;
	public static final int LIGHTMAP_TEXTURE_UNIT = 1;

	public static final ImmutableSet<Integer> WORLD_RESERVED_TEXTURE_UNITS = ImmutableSet.of(0, 1);

	// TODO: In composite programs, there shouldn't be any reserved textures.
	// We need a way to restore these texture bindings.
	public static final ImmutableSet<Integer> COMPOSITE_RESERVED_TEXTURE_UNITS = ImmutableSet.of(1);

	private IrisSamplers() {
		// no construction allowed
	}

	public static void addRenderTargetSamplers(SamplerHolder samplers, Supplier<ImmutableSet<Integer>> flipped,
											   RenderTargets renderTargets, boolean isFullscreenPass) {
		// colortex0,1,2,3 are only able to be sampled from fullscreen passes.
		// Iris could lift this restriction, though I'm not sure if it could cause issues.
		int startIndex = isFullscreenPass ? 0 : 4;

		for (int i = startIndex; i < renderTargets.getRenderTargetCount(); i++) {
			final int index = i;

			IntSupplier sampler = () -> {
				ImmutableSet<Integer> flippedBuffers = flipped.get();
				RenderTarget target = renderTargets.get(index);

				if (flippedBuffers.contains(index)) {
					return target.getAltTexture();
				} else {
					return target.getMainTexture();
				}
			};

			final String name = "colortex" + i;

			// TODO: How do custom textures interact with aliases?

			if (i < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
				String legacyName = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(i);

				// colortex0 is the default sampler in fullscreen passes
				if (i == 0 && isFullscreenPass) {
					samplers.addDefaultSampler(sampler, name, legacyName);
				} else {
					samplers.addDynamicSampler(sampler, name, legacyName);
				}
			} else {
				samplers.addDynamicSampler(sampler, name);
			}
		}
	}

	public static void addNoiseSampler(SamplerHolder samplers, IntSupplier sampler) {
		samplers.addDynamicSampler(sampler, "noisetex");
	}

	public static boolean hasShadowSamplers(SamplerHolder samplers) {
		// TODO: Keep this up to date with the actual definitions.
		// TODO: Don't query image presence using the sampler interface even though the current underlying implementation
		//       is the same.
		ImmutableList<String> shadowSamplers = ImmutableList.of("shadowtex0", "shadowtex0HW", "shadowtex1", "shadowtex1HW", "shadow", "watershadow",
				"shadowcolor", "shadowcolor0", "shadowcolor1", "shadowcolorimg0", "shadowcolorimg1");

		for (String samplerName : shadowSamplers) {
			if (samplers.hasSampler(samplerName)) {
				return true;
			}
		}

		return false;
	}

	public static boolean addShadowSamplers(SamplerHolder samplers, ShadowRenderTargets shadowRenderTargets) {
		boolean usesShadows;

		// TODO: figure this out from parsing the shader source code to be 100% compatible with the legacy
		// shader packs that rely on this behavior.
		boolean waterShadowEnabled = samplers.hasSampler("watershadow");

		if (waterShadowEnabled) {
			usesShadows = true;
			samplers.addDynamicSampler(shadowRenderTargets.getDepthTexture()::getTextureId, "shadowtex0", "watershadow");
			samplers.addDynamicSampler(shadowRenderTargets.getDepthTextureNoTranslucents()::getTextureId, "shadowtex1", "shadow");
		} else {
			usesShadows = samplers.addDynamicSampler(shadowRenderTargets.getDepthTexture()::getTextureId, "shadowtex0", "shadow");
			usesShadows |= samplers.addDynamicSampler(shadowRenderTargets.getDepthTextureNoTranslucents()::getTextureId, "shadowtex1");
		}

		samplers.addDynamicSampler(() -> shadowRenderTargets.getColorTextureId(0), "shadowcolor", "shadowcolor0");
		samplers.addDynamicSampler(() -> shadowRenderTargets.getColorTextureId(1), "shadowcolor1");

		if (shadowRenderTargets.isHardwareFiltered(0)) {
			samplers.addDynamicSampler(shadowRenderTargets.getDepthTexture()::getTextureId, "shadowtex0HW");
		}

		if (shadowRenderTargets.isHardwareFiltered(1)) {
			samplers.addDynamicSampler(shadowRenderTargets.getDepthTextureNoTranslucents()::getTextureId, "shadowtex1HW");
		}

		return usesShadows;
	}

	public static boolean hasPBRSamplers(SamplerHolder samplers) {
		return samplers.hasSampler("normals") || samplers.hasSampler("specular");
	}

	public static void addLevelSamplers(SamplerHolder samplers, WorldRenderingPipeline pipeline, AbstractTexture whitePixel, InputAvailability availability) {
		if (availability.texture) {
			samplers.addExternalSampler(ALBEDO_TEXTURE_UNIT, "tex", "texture", "gtexture");
		} else {
			// TODO: Rebind unbound sampler IDs instead of hardcoding a list...
			samplers.addDynamicSampler(whitePixel::getGlTextureId, "tex", "texture", "gtexture", "gcolor", "colortex0");
		}

		if (availability.lightmap) {
			samplers.addExternalSampler(LIGHTMAP_TEXTURE_UNIT, "lightmap");
		} else {
			samplers.addDynamicSampler(whitePixel::getGlTextureId, "lightmap");
		}

		samplers.addDynamicSampler(pipeline::getCurrentNormalTexture, StateUpdateNotifiers.normalTextureChangeNotifier, "normals");
		samplers.addDynamicSampler(pipeline::getCurrentSpecularTexture, StateUpdateNotifiers.specularTextureChangeNotifier, "specular");
	}

	public static void addWorldDepthSamplers(SamplerHolder samplers, RenderTargets renderTargets) {
		samplers.addDynamicSampler(renderTargets::getDepthTexture, "depthtex0");
		// TODO: Should depthtex2 be made available to gbuffer / shadow programs?
		samplers.addDynamicSampler(renderTargets.getDepthTextureNoTranslucents()::getTextureId, "depthtex1");
	}

	public static void addCompositeSamplers(SamplerHolder samplers, RenderTargets renderTargets) {
		samplers.addDynamicSampler(renderTargets::getDepthTexture, "gdepthtex", "depthtex0");
		samplers.addDynamicSampler(renderTargets.getDepthTextureNoTranslucents()::getTextureId, "depthtex1");
		samplers.addDynamicSampler(renderTargets.getDepthTextureNoHand()::getTextureId, "depthtex2");
	}
}
