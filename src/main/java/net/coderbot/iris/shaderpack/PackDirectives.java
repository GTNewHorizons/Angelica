package net.coderbot.iris.shaderpack;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Getter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.texture.TextureScaleOverride;
import org.joml.Vector2i;

import java.util.Set;

public class PackDirectives {
	@Getter
    private int noiseTextureResolution;
	@Getter
    private float sunPathRotation;
	@Getter
    private float ambientOcclusionLevel;
	@Getter
    private float wetnessHalfLife;
	@Getter
    private float drynessHalfLife;
	@Getter
    private float eyeBrightnessHalfLife;
	@Getter
    private float centerDepthHalfLife;
	@Getter
    private CloudSetting cloudSetting;
	private boolean underwaterOverlay;
	private boolean vignette;
	private boolean sun;
	private boolean moon;
	private boolean rainDepth;
	private boolean separateAo;
	@Getter
    private boolean oldLighting;
	private boolean concurrentCompute;
	@Getter
    private boolean oldHandLight;
	private boolean particlesBeforeDeferred;
	@Getter
    private boolean prepareBeforeShadow;
	private Object2ObjectMap<String, Object2BooleanMap<String>> explicitFlips = new Object2ObjectOpenHashMap<>();
	private Object2ObjectMap<String, TextureScaleOverride> scaleOverrides = new Object2ObjectOpenHashMap<>();

	@Getter
    private final PackRenderTargetDirectives renderTargetDirectives;
	@Getter
    private final PackShadowDirectives shadowDirectives;

	private PackDirectives(Set<Integer> supportedRenderTargets, PackShadowDirectives packShadowDirectives) {
		noiseTextureResolution = 256;
		sunPathRotation = 0.0F;
		ambientOcclusionLevel = 1.0F;
		wetnessHalfLife = 600.0f;
		drynessHalfLife = 200.0f;
		eyeBrightnessHalfLife = 10.0f;
		centerDepthHalfLife = 1.0F;
		renderTargetDirectives = new PackRenderTargetDirectives(supportedRenderTargets);
		shadowDirectives = packShadowDirectives;
	}

	PackDirectives(Set<Integer> supportedRenderTargets, ShaderProperties properties) {
		this(supportedRenderTargets, new PackShadowDirectives(properties));
		cloudSetting = properties.getCloudSetting();
		underwaterOverlay = properties.getUnderwaterOverlay().orElse(false);
		vignette = properties.getVignette().orElse(false);
		sun = properties.getSun().orElse(true);
		moon = properties.getMoon().orElse(true);
		rainDepth = properties.getRainDepth().orElse(false);
		separateAo = properties.getSeparateAo().orElse(false);
		oldLighting = properties.getOldLighting().orElse(false);
		concurrentCompute = properties.getConcurrentCompute().orElse(false);
		oldHandLight = properties.getOldHandLight().orElse(true);
		explicitFlips = properties.getExplicitFlips();
		scaleOverrides = properties.getTextureScaleOverrides();
		particlesBeforeDeferred = properties.getParticlesBeforeDeferred().orElse(false);
		prepareBeforeShadow = properties.getPrepareBeforeShadow().orElse(false);
	}

	PackDirectives(Set<Integer> supportedRenderTargets, PackDirectives directives) {
		this(supportedRenderTargets, new PackShadowDirectives(directives.getShadowDirectives()));
		cloudSetting = directives.cloudSetting;
		separateAo = directives.separateAo;
		oldLighting = directives.oldLighting;
		concurrentCompute = directives.concurrentCompute;
		explicitFlips = directives.explicitFlips;
		scaleOverrides = directives.scaleOverrides;
		particlesBeforeDeferred = directives.particlesBeforeDeferred;
		prepareBeforeShadow = directives.prepareBeforeShadow;
	}

    public boolean underwaterOverlay() {
		return underwaterOverlay;
	}

	public boolean vignette() {
		return vignette;
	}

	public boolean shouldRenderSun() {
		return sun;
	}

	public boolean shouldRenderMoon() {
		return moon;
	}

	public boolean rainDepth() {
		return rainDepth;
	}

	public boolean shouldUseSeparateAo() {
		return separateAo;
	}

    public boolean areParticlesBeforeDeferred() {
		return particlesBeforeDeferred;
	}

	public boolean getConcurrentCompute() {
		return concurrentCompute;
	}

    private static float clamp(float val, float lo, float hi) {
		return Math.max(lo, Math.min(hi, val));
	}

	public void acceptDirectivesFrom(DirectiveHolder directives) {
		renderTargetDirectives.acceptDirectives(directives);
		shadowDirectives.acceptDirectives(directives);

		directives.acceptConstIntDirective("noiseTextureResolution",
				noiseTextureResolution -> this.noiseTextureResolution = noiseTextureResolution);

		directives.acceptConstFloatDirective("sunPathRotation",
				sunPathRotation -> this.sunPathRotation = sunPathRotation);

		directives.acceptConstFloatDirective("ambientOcclusionLevel",
				ambientOcclusionLevel -> this.ambientOcclusionLevel = clamp(ambientOcclusionLevel, 0.0f, 1.0f));

		directives.acceptConstFloatDirective("wetnessHalflife",
			wetnessHalfLife -> this.wetnessHalfLife = wetnessHalfLife);

		directives.acceptConstFloatDirective("drynessHalflife",
			wetnessHalfLife -> this.wetnessHalfLife = wetnessHalfLife);

		directives.acceptConstFloatDirective("eyeBrightnessHalflife",
			eyeBrightnessHalfLife -> this.eyeBrightnessHalfLife = eyeBrightnessHalfLife);

		directives.acceptConstFloatDirective("centerDepthHalflife",
			centerDepthHalfLife -> this.centerDepthHalfLife = centerDepthHalfLife);
	}

	public ImmutableMap<Integer, Boolean> getExplicitFlips(String pass) {
		ImmutableMap.Builder<Integer, Boolean> explicitFlips = ImmutableMap.builder();

		Object2BooleanMap<String> explicitFlipsStr = this.explicitFlips.get(pass);

		if (explicitFlipsStr == null) {
			explicitFlipsStr = Object2BooleanMaps.emptyMap();
		}

		explicitFlipsStr.forEach((buffer, shouldFlip) -> {
			int index = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.indexOf(buffer);

			if (index == -1 && buffer.startsWith("colortex")) {
				final String id = buffer.substring("colortex".length());

				try {
					index = Integer.parseInt(id);
				} catch (NumberFormatException e) {
					// fall through to index == null check for unknown buffer.
				}
			}

			if (index != -1) {
				explicitFlips.put(index, shouldFlip);
			} else {
				Iris.logger.warn("Unknown buffer with ID " + buffer + " specified in flip directive for pass "
						+ pass);
			}
		});

		return explicitFlips.build();
	}

	public Vector2i getTextureScaleOverride(int index, int dimensionX, int dimensionY) {
		final String name = "colortex" + index;

		// TODO: How do custom textures interact with aliases?

		final Vector2i scale = new Vector2i();

		if (index < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
			final String legacyName = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(index);

			if (scaleOverrides.containsKey(legacyName)) {
				scale.set(scaleOverrides.get(legacyName).getX(dimensionX), scaleOverrides.get(legacyName).getY(dimensionY));
			} else if (scaleOverrides.containsKey(name)) {
				scale.set(scaleOverrides.get(name).getX(dimensionX), scaleOverrides.get(name).getY(dimensionY));
			} else {
				scale.set(dimensionX, dimensionY);
			}
		} else if (scaleOverrides.containsKey(name)) {
			scale.set(scaleOverrides.get(name).getX(dimensionX), scaleOverrides.get(name).getY(dimensionY));
		} else {
			scale.set(dimensionX, dimensionY);
		}

		return scale;
	}
}
