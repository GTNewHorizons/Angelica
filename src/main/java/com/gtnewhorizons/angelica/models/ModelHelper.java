package com.gtnewhorizons.angelica.models;

import java.util.Arrays;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Collection of utilities for model implementations.
 */
public final class ModelHelper {
	private ModelHelper() { }

	/** Result from {@link #toFaceIndex(ForgeDirection)} for null values. */
	public static final int NULL_FACE_ID = 6;

	/**
	 * Convenient way to encode faces that may be null.
	 * Null is returned as {@link #NULL_FACE_ID}.
	 * Use {@link #faceFromIndex(int)} to retrieve encoded face.
	 */
	public static int toFaceIndex(ForgeDirection face) {
		return face == null ? NULL_FACE_ID : face.ordinal();
	}

	/**
	 * Use to decode a result from {@link #toFaceIndex(ForgeDirection)}.
	 * Return value will be null if encoded value was null.
	 * Can also be used for no-allocation iteration of {@link ForgeDirection#values()},
	 * optionally including the null face. (Use &lt; or  &lt;= {@link #NULL_FACE_ID}
	 * to exclude or include the null value, respectively.)
	 */
	public static ForgeDirection faceFromIndex(int faceIndex) {
		return FACES[faceIndex];
	}

	/** @see #faceFromIndex(int) */
	private static final ForgeDirection[] FACES = Arrays.copyOf(ForgeDirection.values(), 7);
}
