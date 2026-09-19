package net.coderbot.iris.gbuffer_overrides.matching;

public enum InputAvailability {
	NONE(false, false), TEXTURE(true, false), LIGHTMAP(false, true), BOTH(true, true);

	public static final int NUM_VALUES = 8;
	public static final InputAvailability[] VALUES = values();

	public final boolean texture;
	public final boolean lightmap;

	InputAvailability(boolean texture, boolean lightmap) {
		this.texture = texture;
		this.lightmap = lightmap;
	}

	public static InputAvailability of(boolean texture, boolean lightmap) {
		return VALUES[(texture ? 1 : 0) | (lightmap ? 2 : 0)];
	}

	public static InputAvailability unpack(int packed) {
		return VALUES[packed & 3];
	}

	public int pack() {
		return ordinal();
	}
}
