package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.compat.mojang.Constants;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Objects;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

/**
 * @see <a href="https://github.com/IrisShaders/ShaderDoc/blob/master/uniforms.md#celestial-bodies">Uniforms: Celestial bodies</a>
 */
public final class CelestialUniforms {

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
			.uniformTruncated3f(PER_FRAME, "upPosition", CelestialUniforms::getUpPosition);
	}

	public static float getSunAngle() {
		float skyAngle = getSkyAngle();

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
		return isDay() ? getSunPosition() : getMoonPosition();
	}

	public Vector4f getShadowLightPositionInWorldSpace() {
		return isDay() ? getCelestialPositionInWorldSpace(100.0F) : getCelestialPositionInWorldSpace(-100.0F);
	}

	private Vector4f getCelestialPositionInWorldSpace(float y) {
		Vector4f position = new Vector4f(0.0F, y, 0.0F, 0.0F);

		// TODO: Deduplicate / remove this function.
		Matrix4f celestial = new Matrix4f().identity();

		// This is the same transformation applied by renderSky, however, it's been moved to here.
		// This is because we need the result of it before it's actually performed in vanilla.
        celestial.rotateY(-90.F * Constants.DEGREES_TO_RADIANS);
        celestial.rotateZ(sunPathRotation * Constants.DEGREES_TO_RADIANS);
        celestial.rotateX(getSkyAngle() * 360.0F);

        celestial.transform(position);

        return position;
	}

	private Vector4f getCelestialPosition(float y) {
        Vector4f position = new Vector4f(0.0F, y, 0.0F, 0.0F);

        Matrix4f celestial = new Matrix4f(RenderingState.INSTANCE.getModelViewMatrix());
		// This is the same transformation applied by renderSky, however, it's been moved to here.
		// This is because we need the result of it before it's actually performed in vanilla.
        celestial.rotateY(-90.F * Constants.DEGREES_TO_RADIANS);
        celestial.rotateZ(sunPathRotation * Constants.DEGREES_TO_RADIANS);
        celestial.rotateX(getSkyAngle() * 360.0F);

        position = celestial.transform(position);

        return position;
	}

	private static Vector4f getUpPosition() {
        Vector4f upVector = new Vector4f(0.0F, 100.0F, 0.0F, 0.0F);

		// Get the current model view matrix, since that is the basis of the celestial model view matrix
        Matrix4f preCelestial = new Matrix4f(RenderingState.INSTANCE.getModelViewMatrix());

		// Apply the fixed -90.0F degrees rotation to mirror the same transformation in renderSky.
		// But, notably, skip the rotation by the skyAngle.
        preCelestial.rotateY(-90.F * Constants.DEGREES_TO_RADIANS);

		// Use this matrix to transform the vector.
        upVector = preCelestial.transform(upVector);

        return upVector;
	}

	public static boolean isDay() {
		// Determine whether it is day or night based on the sky angle.
		//
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
