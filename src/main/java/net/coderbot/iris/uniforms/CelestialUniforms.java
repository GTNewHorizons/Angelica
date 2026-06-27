package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.compat.etfuturum.EndFlashCompat;
import com.gtnewhorizons.angelica.compat.mojang.Constants;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.WorldProviderEnd;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Objects;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

/**
 * @see <a href="https://github.com/IrisShaders/ShaderDoc/blob/master/uniforms.md#celestial-bodies">Uniforms: Celestial bodies</a>
 */
public final class CelestialUniforms {
	private static final Vector4f upPositionCache = new Vector4f();
	private static final Matrix4f upMatrixCache = new Matrix4f();
	private static final Vector4f ZERO_VECTOR_4f = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);

	private final Vector4f positionCache = new Vector4f();
	private final Matrix4f celestialMatrixCache = new Matrix4f();
	private final Vector4f endFlashPositionCache = new Vector4f();
	private final Matrix4f endFlashMatrixCache = new Matrix4f();
    private final float sunPathRotation;

	public CelestialUniforms(float sunPathRotation) {
		this.sunPathRotation = sunPathRotation;
	}

	public void addCelestialUniforms(UniformHolder uniforms) {
		uniforms
			.uniform1f(PER_FRAME, "sunAngle", CelestialUniforms::getSunAngle)
			.uniformTruncated3f(PER_FRAME, "sunPosition", this::getSunPosition)
			.uniformTruncated3f(PER_FRAME, "moonPosition", this::getMoonPosition)
			.uniform1f(PER_FRAME, "shadowAngle", CelestialUniforms::getShadowAngle)
			.uniformTruncated3f(PER_FRAME, "shadowLightPosition", this::getShadowLightPosition)
			.uniformTruncated3f(PER_FRAME, "endFlashPosition", this::getEndFlashPosition)
			.uniformTruncated3f(PER_FRAME, "upPosition", CelestialUniforms::getUpPosition);
	}

	private Vector4f getEndFlashPosition() {
		final WorldClient world = Minecraft.getMinecraft().theWorld;
		if (world == null || !(world.provider instanceof WorldProviderEnd) || !EndFlashCompat.isAvailable()) {
			return ZERO_VECTOR_4f;
		}

		final float yAngle = EndFlashCompat.getYAngle();
		final float xAngle = EndFlashCompat.getXAngle();

		endFlashPositionCache.set(0.0F, 100.0F, 0.0F, 0.0F);
		endFlashMatrixCache.set(RenderingState.INSTANCE.getModelViewMatrix());
		endFlashMatrixCache.rotateY((180.0F - yAngle) * Constants.DEGREES_TO_RADIANS);
		endFlashMatrixCache.rotateX((-90.0F - xAngle) * Constants.DEGREES_TO_RADIANS);
		endFlashMatrixCache.transform(endFlashPositionCache);
		return endFlashPositionCache;
	}

	private Vector4f getEndFlashPositionInWorldSpace() {
		final float yAngle = EndFlashCompat.getYAngle();
		final float xAngle = EndFlashCompat.getXAngle();

		endFlashPositionCache.set(0.0F, 100.0F, 0.0F, 0.0F);
		endFlashMatrixCache.identity();
		endFlashMatrixCache.rotateY((180.0F - yAngle) * Constants.DEGREES_TO_RADIANS);
		endFlashMatrixCache.rotateX((-90.0F - xAngle) * Constants.DEGREES_TO_RADIANS);
		endFlashMatrixCache.transform(endFlashPositionCache);
		return endFlashPositionCache;
	}

	private static boolean isEndFlashShadowActive() {
		final WorldClient world = Minecraft.getMinecraft().theWorld;
		return world != null && world.provider instanceof WorldProviderEnd
			&& EndFlashCompat.isAvailable()
			&& Iris.getPipelineManager().getPipeline().map(WorldRenderingPipeline::supportsEndFlash).orElse(false);
	}

	public static float getSunAngle() {
		final float skyAngle = getSkyAngle();

		if (skyAngle < 0.75F) {
			return skyAngle + 0.25F;
		} else {
			return skyAngle - 0.75F;
		}
	}

	private static float getShadowAngle() {
		float shadowAngle = getSunAngle();

		if (!isDay()) {
			shadowAngle -= 0.5F;
		}

		return shadowAngle;
	}

	private Vector4f getSunPosition() {
		return getCelestialPosition(100.0F);
	}

	private Vector4f getMoonPosition() {
		return getCelestialPosition(-100.0F);
	}

	public Vector4f getShadowLightPosition() {
		if (isEndFlashShadowActive()) {
			return getEndFlashPosition();
		}
		return isDay() ? getSunPosition() : getMoonPosition();
	}

	public Vector4f getShadowLightPositionInWorldSpace() {
		if (isEndFlashShadowActive()) {
			return getEndFlashPositionInWorldSpace();
		}
		return isDay() ? getCelestialPositionInWorldSpace(100.0F) : getCelestialPositionInWorldSpace(-100.0F);
	}

	private Vector4f getCelestialPositionInWorldSpace(float y) {
		positionCache.set(0.0F, y, 0.0F, 0.0F);

		// TODO: Deduplicate / remove this function.
		celestialMatrixCache.identity();

		// This is the same transformation applied by renderSky, however, it's been moved to here.
		// This is because we need the result of it before it's actually performed in vanilla.
        celestialMatrixCache.rotateY(-90.F * Constants.DEGREES_TO_RADIANS);
        celestialMatrixCache.rotateZ(sunPathRotation * Constants.DEGREES_TO_RADIANS);
        celestialMatrixCache.rotateX(getSkyAngle() * 360.0F * Constants.DEGREES_TO_RADIANS);

        celestialMatrixCache.transform(positionCache);

        return positionCache;
	}

	private Vector4f getCelestialPosition(float y) {
        positionCache.set(0.0F, y, 0.0F, 0.0F);

        celestialMatrixCache.set(RenderingState.INSTANCE.getModelViewMatrix());
		// This is the same transformation applied by renderSky, however, it's been moved to here.
		// This is because we need the result of it before it's actually performed in vanilla.
        celestialMatrixCache.rotateY(-90.F * Constants.DEGREES_TO_RADIANS);
        celestialMatrixCache.rotateZ(sunPathRotation * Constants.DEGREES_TO_RADIANS);
        celestialMatrixCache.rotateX(getSkyAngle() * 360.0F * Constants.DEGREES_TO_RADIANS);

        celestialMatrixCache.transform(positionCache);

        return positionCache;
	}

	private static Vector4f getUpPosition() {
        upPositionCache.set(0.0F, 100.0F, 0.0F, 0.0F);

		// Get the current model view matrix, since that is the basis of the celestial model view matrix
        upMatrixCache.set(RenderingState.INSTANCE.getModelViewMatrix());

		// Apply the fixed -90.0F degrees rotation to mirror the same transformation in renderSky.
		// But, notably, skip the rotation by the skyAngle.
        upMatrixCache.rotateY(-90.F * Constants.DEGREES_TO_RADIANS);

		// Use this matrix to transform the vector.
        upMatrixCache.transform(upPositionCache);

        return upPositionCache;
	}

	public static boolean isDay() {
		// Determine whether it is day or night based on the sky angle.
		// World#isDay appears to do some nontrivial calculations that appear to not entirely work for us here.
		return getSunAngle() <= 0.5;
	}

	private static WorldClient getWorld() {
		return Objects.requireNonNull(Minecraft.getMinecraft().theWorld);
	}

	private static float getSkyAngle() {
		return getWorld().getCelestialAngle(CapturedRenderingState.INSTANCE.getTickDelta());
	}
}
