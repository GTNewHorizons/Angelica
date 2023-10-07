package net.coderbot.batchedentityrendering.impl;

public interface WrappableRenderType {
	/**
	 * Returns the underlying wrapped RenderType. Might return itself if this RenderType doesn't wrap anything.
	 */
	RenderType unwrap();
}
