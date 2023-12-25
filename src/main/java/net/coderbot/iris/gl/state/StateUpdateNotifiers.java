package net.coderbot.iris.gl.state;

/**
 * Holds some standard update notifiers for various elements of GL state. Currently, this class has a few listeners for
 * fog-related values.
 */
public class StateUpdateNotifiers {
	public static ValueUpdateNotifier fogToggleNotifier;
	public static ValueUpdateNotifier fogModeNotifier;
	public static ValueUpdateNotifier fogStartNotifier;
	public static ValueUpdateNotifier fogEndNotifier;
	public static ValueUpdateNotifier fogDensityNotifier;
	public static ValueUpdateNotifier blendFuncNotifier;
	public static ValueUpdateNotifier bindTextureNotifier;
	public static ValueUpdateNotifier normalTextureChangeNotifier;
	public static ValueUpdateNotifier specularTextureChangeNotifier;
	public static ValueUpdateNotifier phaseChangeNotifier;
}
