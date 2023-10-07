package net.coderbot.iris.uniforms;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.shaderpack.DimensionId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

import java.util.Objects;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_TICK;

public final class WorldTimeUniforms {
	private WorldTimeUniforms() {
	}

	/**
	 * Makes world time uniforms available to the given program
	 *
	 * @param uniforms the program to make the uniforms available to
	 */
	public static void addWorldTimeUniforms(UniformHolder uniforms) {
		uniforms
			.uniform1i(PER_TICK, "worldTime", WorldTimeUniforms::getWorldDayTime)
			.uniform1i(PER_TICK, "worldDay", WorldTimeUniforms::getWorldDay)
			.uniform1i(PER_TICK, "moonPhase", () -> getWorld().getMoonPhase());
	}

	static int getWorldDayTime() {
		long timeOfDay = getWorld().getWorldTime();

		if (Iris.getCurrentDimension() == DimensionId.END || Iris.getCurrentDimension() == DimensionId.NETHER) {
			// If the dimension is the nether or the end, don't override the fixed time.
			// This was an oversight in versions before and including 1.2.5 causing inconsistencies, such as Complementary's ender beams not moving.
			return (int) (timeOfDay % 24000L);
		}

		long dayTime = ((DimensionTypeAccessor) getWorld().dimensionType()).getFixedTime()
																		  .orElse(timeOfDay % 24000L);

		return (int) dayTime;
	}

	private static int getWorldDay() {
		long timeOfDay = getWorld().getWorldTime();
		long day = timeOfDay / 24000L;

		return (int) day;
	}

	private static WorldClient getWorld() {
		return Objects.requireNonNull(Minecraft.getMinecraft().theWorld);
	}
}
