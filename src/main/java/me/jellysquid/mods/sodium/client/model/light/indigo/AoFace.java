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

package me.jellysquid.mods.sodium.client.model.light.indigo;

import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import net.minecraftforge.common.util.ForgeDirection;

import static me.jellysquid.mods.sodium.client.model.light.indigo.AoVertexClampFunction.CLAMP_FUNC;
import static net.minecraftforge.common.util.ForgeDirection.*;

/**
 * Adapted from vanilla BlockModelRenderer.AoCalculator.
 */
enum AoFace {
	AOF_DOWN(new ForgeDirection[] { WEST, EAST, NORTH, SOUTH }, (q, i) -> CLAMP_FUNC.clamp(q.getY(i)), (q, i, w) -> {
		final float u = CLAMP_FUNC.clamp(q.getX(i));
		final float v = CLAMP_FUNC.clamp(q.getZ(i));
		w[0] = (1 - u) * v;
		w[1] = (1 - u) * (1 - v);
		w[2] = u * (1 - v);
		w[3] = u * v;
	}),
	AOF_UP(new ForgeDirection[] { EAST, WEST, NORTH, SOUTH }, (q, i) -> 1 - CLAMP_FUNC.clamp(q.getY(i)), (q, i, w) -> {
		final float u = CLAMP_FUNC.clamp(q.getX(i));
		final float v = CLAMP_FUNC.clamp(q.getZ(i));
		w[0] = u * v;
		w[1] = u * (1 - v);
		w[2] = (1 - u) * (1 - v);
		w[3] = (1 - u) * v;
	}),
	AOF_NORTH(new ForgeDirection[] { UP, DOWN, EAST, WEST }, (q, i) -> CLAMP_FUNC.clamp(q.getZ(i)), (q, i, w) -> {
		final float u = CLAMP_FUNC.clamp(q.getY(i));
		final float v = CLAMP_FUNC.clamp(q.getX(i));
		w[0] = u * (1 - v);
		w[1] = u * v;
		w[2] = (1 - u) * v;
		w[3] = (1 - u) * (1 - v);
	}),
	AOF_SOUTH(new ForgeDirection[] { WEST, EAST, DOWN, UP }, (q, i) -> 1 - CLAMP_FUNC.clamp(q.getZ(i)), (q, i, w) -> {
		final float u = CLAMP_FUNC.clamp(q.getY(i));
		final float v = CLAMP_FUNC.clamp(q.getX(i));
		w[0] = u * (1 - v);
		w[1] = (1 - u) * (1 - v);
		w[2] = (1 - u) * v;
		w[3] = u * v;
	}),
	AOF_WEST(new ForgeDirection[] { UP, DOWN, NORTH, SOUTH }, (q, i) -> CLAMP_FUNC.clamp(q.getX(i)), (q, i, w) -> {
		final float u = CLAMP_FUNC.clamp(q.getY(i));
		final float v = CLAMP_FUNC.clamp(q.getZ(i));
		w[0] = u * v;
		w[1] = u * (1 - v);
		w[2] = (1 - u) * (1 - v);
		w[3] = (1 - u) * v;
	}),
	AOF_EAST(new ForgeDirection[] { DOWN, UP, NORTH, SOUTH }, (q, i) -> 1 - CLAMP_FUNC.clamp(q.getX(i)), (q, i, w) -> {
		final float u = CLAMP_FUNC.clamp(q.getY(i));
		final float v = CLAMP_FUNC.clamp(q.getZ(i));
		w[0] = (1 - u) * v;
		w[1] = (1 - u) * (1 - v);
		w[2] = u * (1 - v);
		w[3] = u * v;
	});

	final ForgeDirection[] neighbors;
	final WeightFunction weightFunc;
	final Vertex2Float depthFunc;

	AoFace(ForgeDirection[] faces, Vertex2Float depthFunc, WeightFunction weightFunc) {
		this.neighbors = faces;
		this.depthFunc = depthFunc;
		this.weightFunc = weightFunc;
	}

    // The order of these is important
	private static final AoFace[] values = {
        AOF_DOWN,
        AOF_UP,
        AOF_NORTH,
        AOF_SOUTH,
        AOF_WEST,
        AOF_EAST
    };

	public static AoFace get(ForgeDirection direction) {
		return values[direction.ordinal()];
	}

	/**
	 * Implementations handle bilinear interpolation of a point on a light face
	 * by computing weights for each corner of the light face. Relies on the fact
	 * that each face is a unit cube. Uses coordinates from axes orthogonal to face
	 * as distance from the edge of the cube, flipping as needed. Multiplying distance
	 * coordinate pairs together gives sub-area that are the corner weights.
	 * Weights sum to 1 because it is a unit cube. Values are stored in the provided array.
	 */
	@FunctionalInterface
	interface WeightFunction {
		void apply(ModelQuadView q, int vertexIndex, float[] out);
	}

	@FunctionalInterface
	interface Vertex2Float {
		float apply(ModelQuadView q, int vertexIndex);
	}
}
