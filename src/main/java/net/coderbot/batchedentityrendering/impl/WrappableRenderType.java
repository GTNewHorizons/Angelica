package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizons.angelica.compat.toremove.RenderLayer;

public interface WrappableRenderType {
	/**
	 * Returns the underlying wrapped RenderType. Might return itself if this RenderType doesn't wrap anything.
	 */
	RenderLayer unwrap();
}
