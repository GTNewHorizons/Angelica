package net.coderbot.iris.gl.state;

/**
 * A notifier that can trigger uniform updates when values change.
 */
public interface ValueUpdateNotifier {
	/** No-op notifier for uniforms that are polled per-frame rather than push-notified */
	ValueUpdateNotifier NONE = listener -> {};

	/**
	 * Sets up a listener with this notifier.
	 */
	void setListener(Runnable listener);
}
