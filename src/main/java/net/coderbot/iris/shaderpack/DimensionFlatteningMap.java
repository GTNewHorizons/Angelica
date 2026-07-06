package net.coderbot.iris.shaderpack;

import java.util.HashMap;
import java.util.Map;

public class DimensionFlatteningMap {
	private static final Map<String, String> MODERN_TO_LEGACY = new HashMap<>();

	static {
		MODERN_TO_LEGACY.put("minecraft:overworld", "Overworld");
		MODERN_TO_LEGACY.put("minecraft:the_nether", "Nether");
		MODERN_TO_LEGACY.put("minecraft:the_end", "The End");
	}

	/**
	 * Maps a modern dimension ID to its 1.7.10 string name.
	 */
	public static String toLegacyName(String dimensionName) {
		return MODERN_TO_LEGACY.getOrDefault(dimensionName, dimensionName);
	}
}
