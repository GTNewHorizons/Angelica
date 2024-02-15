/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gtnewhorizons.angelica.models;

import com.gtnewhorizons.angelica.compat.mojang.Axis;
import org.joml.Vector3f;


/**
 * Static routines of general utility for renderer implementations.
 * Renderers are not required to use these helpers, but they were
 * designed to be usable without the default renderer.
 */
public abstract class GeometryHelper {
	private GeometryHelper() { }

	/** set when a quad touches all four corners of a unit cube. */
	public static final int CUBIC_FLAG = 1;

	/** set when a quad is parallel to (but not necessarily on) a its light face. */
	public static final int AXIS_ALIGNED_FLAG = CUBIC_FLAG << 1;

	/** set when a quad is coplanar with its light face. Implies {@link #AXIS_ALIGNED_FLAG} */
	public static final int LIGHT_FACE_FLAG = AXIS_ALIGNED_FLAG << 1;

	/** how many bits quad header encoding should reserve for encoding geometry flags. */
	public static final int FLAG_BIT_COUNT = 3;

	private static final float EPS_MIN = 0.0001f;
	private static final float EPS_MAX = 1.0f - EPS_MIN;

	/**
	 * Simple 4-way compare, doesn't handle NaN values.
	 */
	public static float min(float a, float b, float c, float d) {
		final float x = a < b ? a : b;
		final float y = c < d ? c : d;
		return x < y ? x : y;
	}

	/**
	 * Simple 4-way compare, doesn't handle NaN values.
	 */
	public static float max(float a, float b, float c, float d) {
		final float x = a > b ? a : b;
		final float y = c > d ? c : d;
		return x > y ? x : y;
	}

	/**
	 * @see #longestAxis(float, float, float)
	 */
	public static Axis longestAxis(Vector3f vec) {
		return longestAxis(vec.x(), vec.y(), vec.z());
	}

	/**
	 * Identifies the largest (max absolute magnitude) component (X, Y, Z) in the given vector.
	 */
	public static Axis longestAxis(float normalX, float normalY, float normalZ) {
		Axis result = Axis.Y;
		float longest = Math.abs(normalY);
		float a = Math.abs(normalX);

		if (a > longest) {
			result = Axis.X;
			longest = a;
		}

		return Math.abs(normalZ) > longest
				? Axis.Z : result;
	}
}
