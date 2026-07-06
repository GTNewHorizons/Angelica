package net.coderbot.iris.uniforms;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_TICK;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import java.util.Objects;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

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
        final WorldClient world = getWorld();
        return (int) ((AngelicaConfig.useTotalWorldTime ? world.getTotalWorldTime() : world.getWorldTime()) % 24000L);

    //  long dayTime = ((DimensionTypeAccessor) getWorld().dimensionType()).getFixedTime().orElse(timeOfDay % 24000L);
	}

	private static int getWorldDay() {
        return (int) (getWorld().getTotalWorldTime() / 24000L);
	}

	private static WorldClient getWorld() {
		return Objects.requireNonNull(Minecraft.getMinecraft().theWorld);
	}
}
