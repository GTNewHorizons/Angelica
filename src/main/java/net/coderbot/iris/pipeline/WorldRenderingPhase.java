package net.coderbot.iris.pipeline;

import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;

public enum WorldRenderingPhase {
	NONE,
	SKY,
	SUNSET,
	CUSTOM_SKY, // Unused, just here to match OptiFine ordinals
	SUN,
	MOON,
	STARS,
	VOID,
	TERRAIN_SOLID,
	TERRAIN_CUTOUT_MIPPED,
	TERRAIN_CUTOUT,
	ENTITIES,
	BLOCK_ENTITIES,
	DESTROY,
	OUTLINE,
	DEBUG,
	HAND_SOLID,
	TERRAIN_TRANSLUCENT,
	TRIPWIRE,
	PARTICLES,
	CLOUDS,
	RAIN_SNOW,
	WORLD_BORDER,
	HAND_TRANSLUCENT;

	public static WorldRenderingPhase fromTerrainRenderType(RenderLayer renderType) {
		if (renderType == RenderLayer.solid()) {
			return WorldRenderingPhase.TERRAIN_SOLID;
		} else if (renderType == RenderLayer.cutout()) {
			return WorldRenderingPhase.TERRAIN_CUTOUT;
		} else if (renderType == RenderLayer.cutoutMipped()) {
			return WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED;
		} else if (renderType == RenderLayer.translucent()) {
			return WorldRenderingPhase.TERRAIN_TRANSLUCENT;
		} else if (renderType == RenderLayer.tripwire()) {
			return WorldRenderingPhase.TRIPWIRE;
		} else {
			throw new IllegalStateException("Illegal render type!");
		}
	}
}
