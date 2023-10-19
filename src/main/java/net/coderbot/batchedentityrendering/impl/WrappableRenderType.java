package net.coderbot.batchedentityrendering.impl;

import net.coderbot.iris.compat.mojang.RenderType;

public interface WrappableRenderType {
	/**
	 * Returns the underlying wrapped RenderType. Might return itself if this RenderType doesn't wrap anything.
	 */
	RenderType unwrap();
}
