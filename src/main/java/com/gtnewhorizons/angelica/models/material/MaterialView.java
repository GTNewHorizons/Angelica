package com.gtnewhorizons.angelica.models.material;

import com.gtnewhorizons.angelica.models.fapi.TriState;

/**
 * Getter methods for {@link RenderMaterial} (immutable) and {@link MaterialFinder} (mutable).
 *
 * <p>Values returned by the getters may not necessarily be identical to those requested in the {@link MaterialFinder}.
 * The renderer may choose different values that are sufficiently representative for its own processing.
 */
public interface MaterialView {
	/**
	 * @see MaterialFinder#blendMode(BlendMode)
	 */
	BlendMode blendMode();

	/**
	 * @see MaterialFinder#disableColorIndex(boolean)
	 */
	boolean disableColorIndex();

	/**
	 * @see MaterialFinder#emissive(boolean)
	 */
	boolean emissive();

	/**
	 * @see MaterialFinder#disableDiffuse(boolean)
	 */
	boolean disableDiffuse();

	/**
	 * @see MaterialFinder#ambientOcclusion(TriState)
	 */
	TriState ambientOcclusion();

	/**
	 * @see MaterialFinder#glint(TriState)
	 */
	TriState glint();
}
