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

import com.gtnewhorizons.angelica.api.BlockPos;
import com.gtnewhorizons.angelica.api.MutableBlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockPosImpl;
import me.jellysquid.mods.sodium.client.model.light.LightPipeline;
import me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.quad.Quad;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.minecraft.block.Block;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3f;

import static me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFlags.*;
import static me.jellysquid.mods.sodium.client.util.MathUtil.fuzzy_eq;
import static net.minecraftforge.common.util.ForgeDirection.*;

/**
 * Adaptation of inner, non-static class in BlockModelRenderer that serves same purpose.
 */
public class AoCalculator implements LightPipeline {
	/**
	 * Vanilla models with cubic quads have vertices in a certain order, which allows
	 * us to map them using a lookup. Adapted from enum in vanilla AoCalculator.
	 */
	private static final int[][] VERTEX_MAP = new int[6][4];
	static {
		VERTEX_MAP[DOWN.ordinal()] = new int[] { 0, 1, 2, 3 };
		VERTEX_MAP[UP.ordinal()] = new int[] { 2, 3, 0, 1 };
		VERTEX_MAP[NORTH.ordinal()] = new int[] { 3, 0, 1, 2 };
		VERTEX_MAP[SOUTH.ordinal()] = new int[] { 0, 1, 2, 3 };
		VERTEX_MAP[WEST.ordinal()] = new int[] { 3, 0, 1, 2 };
		VERTEX_MAP[EAST.ordinal()] = new int[] { 1, 2, 3, 0 };
	}

	private final MutableBlockPos lightPos = new BlockPosImpl();
	private final MutableBlockPos searchPos = new BlockPosImpl();
	//protected final BlockRenderInfo blockInfo;
	protected final LightDataAccess lightData;

	public int light(BlockPos pos, Block state) {
		return AoCalculator.getLightmapCoordinates(lightData, state, pos);
	}

	public float ao(BlockPos pos, Block state) {
		return AoLuminanceFix.INSTANCE.apply(lightData.getWorld(), pos, state);
	}

	/** caches results of {@link #computeFace(BlockPos, ForgeDirection, boolean, boolean)} for the current block. */
	private final AoFaceData[] faceData = new AoFaceData[24];

	/** indicates which elements of {@link #faceData} have been computed for the current block. */
	private int completionFlags = 0;

	/** holds per-corner weights - used locally to avoid new allocation. */
	private final float[] w = new float[4];

	// outputs
	public final float[] ao = new float[4];
	public final int[] light = new int[4];

	public AoCalculator(LightDataAccess lda) {
		lightData = lda;
		//this.blockInfo = blockInfo;

		for (int i = 0; i < 24; i++) {
			faceData[i] = new AoFaceData();
		}
	}

	/** call at start of each new block. */
	public void clear() {
		completionFlags = 0;
	}


    public void calculate(ModelQuadView quad, BlockPosImpl pos, QuadLightData out, ForgeDirection cullFace, ForgeDirection face, boolean shade) {
		clear();
        calcEnhanced(quad, pos, shade);
	}

	private void calcEnhanced(ModelQuadView quad, BlockPos pos, boolean hasShade) {
		switch (quad.getFlags()) {
		case FRAPI_AXIS_ALIGNED_FLAG | FRAPI_LIGHT_FACE_FLAG:
		case FRAPI_AXIS_ALIGNED_FLAG | FRAPI_NON_CUBIC_FLAG | FRAPI_LIGHT_FACE_FLAG:
			vanillaPartialFace(pos, quad, quad.getLightFace(), true, hasShade);
			break;

		case FRAPI_AXIS_ALIGNED_FLAG:
		case FRAPI_AXIS_ALIGNED_FLAG | FRAPI_NON_CUBIC_FLAG:
			blendedPartialFace(pos, quad, quad.getLightFace(), hasShade);
			break;

		default:
			irregularFace(pos, quad, hasShade);
			break;
		}
	}

	private void vanillaPartialFace(BlockPos pos, ModelQuadView quad, ForgeDirection lightFace, boolean isOnLightFace, boolean shade) {
		AoFaceData faceData = computeFace(pos, lightFace, isOnLightFace, shade);
		final AoFace.WeightFunction wFunc = AoFace.get(lightFace).weightFunc;
		final float[] w = this.w;

		for (int i = 0; i < 4; i++) {
			wFunc.apply(quad, i, w);
			light[i] = faceData.weightedCombinedLight(w);
			ao[i] = faceData.weigtedAo(w);
		}
	}

	/** used in {@link #blendedInsetFace(BlockPos, ModelQuadView quad, int vertexIndex, ForgeDirection lightFace, boolean shade)} as return variable to avoid new allocation. */
	AoFaceData tmpFace = new AoFaceData();

	/** Returns linearly interpolated blend of outer and inner face based on depth of vertex in face. */
	private AoFaceData blendedInsetFace(BlockPos pos, ModelQuadView quad, int vertexIndex, ForgeDirection lightFace, boolean shade) {
		final float w1 = AoFace.get(lightFace).depthFunc.apply(quad, vertexIndex);
		final float w0 = 1 - w1;
		return AoFaceData.weightedMean(computeFace(pos, lightFace, true, shade), w0, computeFace(pos, lightFace, false, shade), w1, tmpFace);
	}

	/**
	 * Like {@link #blendedInsetFace(BlockPos, ModelQuadView quad, int vertexIndex, ForgeDirection lightFace, boolean shade)} but optimizes if depth is 0 or 1.
	 * Used for irregular faces when depth varies by vertex to avoid unneeded interpolation.
	 */
	private AoFaceData gatherInsetFace(BlockPos pos, ModelQuadView quad, int vertexIndex, ForgeDirection lightFace, boolean shade) {
		final float w1 = AoFace.get(lightFace).depthFunc.apply(quad, vertexIndex);

		if (fuzzy_eq(w1, 0)) {
			return computeFace(pos, lightFace, true, shade);
		} else if (fuzzy_eq(w1, 1)) {
			return computeFace(pos, lightFace, false, shade);
		} else {
			final float w0 = 1 - w1;
			return AoFaceData.weightedMean(computeFace(pos, lightFace, true, shade), w0, computeFace(pos, lightFace, false, shade), w1, tmpFace);
		}
	}

	private void blendedPartialFace(BlockPos pos, ModelQuadView quad, ForgeDirection lightFace, boolean shade) {
		AoFaceData faceData = blendedInsetFace(pos, quad, 0, lightFace, shade);
		final AoFace.WeightFunction wFunc = AoFace.get(lightFace).weightFunc;

		for (int i = 0; i < 4; i++) {
			wFunc.apply(quad, i, w);
			light[i] = faceData.weightedCombinedLight(w);
			ao[i] = faceData.weigtedAo(w);
		}
	}

	private void irregularFace(BlockPos pos, ModelQuadView quad, boolean shade) {
        if (!(quad instanceof Quad q))
            throw new RuntimeException("Found " + quad.getClass() + ", expected a different Quad!");


		final Vector3f faceNorm = q.getNormal();
		Vector3f normal;
		final float[] w = this.w;
		final float[] aoResult = this.ao;

        for (int i = 0; i < 4; i++) {
            // FIXME: support per-vertex normal vectors
			normal = faceNorm;
			float ao = 0, sky = 0, block = 0, maxAo = 0;
			int maxSky = 0, maxBlock = 0;

			final float x = normal.x();

			if (!fuzzy_eq(0f, x)) {
				final ForgeDirection face = x > 0 ? EAST : WEST;
				final AoFaceData fd = gatherInsetFace(pos, quad, i, face, shade);
				AoFace.get(face).weightFunc.apply(quad, i, w);
				final float n = x * x;
				final float a = fd.weigtedAo(w);
				final int s = fd.weigtedSkyLight(w);
				final int b = fd.weigtedBlockLight(w);
				ao += n * a;
				sky += n * s;
				block += n * b;
				maxAo = a;
				maxSky = s;
				maxBlock = b;
			}

			final float y = normal.y();

			if (!fuzzy_eq(0f, y)) {
				final ForgeDirection face = y > 0 ? UP : DOWN;
				final AoFaceData fd = gatherInsetFace(pos, quad, i, face, shade);
				AoFace.get(face).weightFunc.apply(quad, i, w);
				final float n = y * y;
				final float a = fd.weigtedAo(w);
				final int s = fd.weigtedSkyLight(w);
				final int b = fd.weigtedBlockLight(w);
				ao += n * a;
				sky += n * s;
				block += n * b;
				maxAo = Math.max(maxAo, a);
				maxSky = Math.max(maxSky, s);
				maxBlock = Math.max(maxBlock, b);
			}

			final float z = normal.z();

			if (!fuzzy_eq(0f, z)) {
				final ForgeDirection face = z > 0 ? SOUTH : NORTH;
				final AoFaceData fd = gatherInsetFace(pos, quad, i, face, shade);
				AoFace.get(face).weightFunc.apply(quad, i, w);
				final float n = z * z;
				final float a = fd.weigtedAo(w);
				final int s = fd.weigtedSkyLight(w);
				final int b = fd.weigtedBlockLight(w);
				ao += n * a;
				sky += n * s;
				block += n * b;
				maxAo = Math.max(maxAo, a);
				maxSky = Math.max(maxSky, s);
				maxBlock = Math.max(maxBlock, b);
			}

			aoResult[i] = (ao + maxAo) * 0.5f;
			this.light[i] = (((int) ((sky + maxSky) * 0.5f) & 0xF0) << 16) | ((int) ((block + maxBlock) * 0.5f) & 0xF0);
		}
	}

	private AoFaceData computeFace(BlockPos pos, ForgeDirection lightFace, boolean isOnBlockFace, boolean shade) {
		final int faceDataIndex = shade ? (isOnBlockFace ? lightFace.ordinal() : lightFace.ordinal() + 6) : (isOnBlockFace ? lightFace.ordinal() + 12 : lightFace.ordinal() + 18);
		final int mask = 1 << faceDataIndex;
		final AoFaceData result = faceData[faceDataIndex];

		if ((completionFlags & mask) == 0) {
			completionFlags |= mask;
			computeFace(pos, result, lightFace, isOnBlockFace, shade);
		}

		return result;
	}

	/**
	 * Computes smoothed brightness and Ao shading for four corners of a block face.
	 * Outer block face is what you normally see and what you get when the second
	 * parameter is true. Inner is light *within* the block and usually darker.
	 * It is blended with the outer face for inset surfaces, but is also used directly
	 * in vanilla logic for some blocks that aren't full opaque cubes.
	 * Except for parameterization, the logic itself is practically identical to vanilla.
	 */
	private void computeFace(BlockPos pos, AoFaceData result, ForgeDirection lightFace, boolean isOnBlockFace, boolean shade) {
		final WorldSlice world = lightData.getWorld();
		final Block block = world.getBlock(pos);
		final MutableBlockPos lightPos = this.lightPos;
		final MutableBlockPos searchPos = this.searchPos;
		Block searchState;

		if (isOnBlockFace) {
			lightPos.set(pos, lightFace);
		} else {
			lightPos.set(pos);
		}

		AoFace aoFace = AoFace.get(lightFace);

		// Vanilla was further offsetting the positions for opaque block checks in the
		// direction of the light face, but it was actually mis-sampling and causing
		// visible artifacts in certain situations

		searchPos.set(lightPos, aoFace.neighbors[0]);
		searchState = world.getBlock(searchPos);
		final int light0 = light(searchPos, searchState);
		final float ao0 = ao(searchPos, searchState);
		final boolean em0 = hasEmissiveLighting(world, searchPos, searchState);

        // TODO: is this the correct re
        final boolean isClear0 = !searchState.isOpaqueCube() || searchState.getLightOpacity() == 0;

		searchPos.set(lightPos, aoFace.neighbors[1]);
		searchState = world.getBlock(searchPos);
		final int light1 = light(searchPos, searchState);
		final float ao1 = ao(searchPos, searchState);
		final boolean em1 = hasEmissiveLighting(world, searchPos, searchState);

        final boolean isClear1 = !searchState.isOpaqueCube() || searchState.getLightOpacity() == 0;

		searchPos.set(lightPos, aoFace.neighbors[2]);
		searchState = world.getBlock(searchPos);
		final int light2 = light(searchPos, searchState);
		final float ao2 = ao(searchPos, searchState);
		final boolean em2 = hasEmissiveLighting(world, searchPos, searchState);

		final boolean isClear2 = !searchState.isOpaqueCube() || searchState.getLightOpacity() == 0;

		searchPos.set(lightPos, aoFace.neighbors[3]);
		searchState = world.getBlock(searchPos);
		final int light3 = light(searchPos, searchState);
		final float ao3 = ao(searchPos, searchState);
		final boolean em3 = hasEmissiveLighting(world, searchPos, searchState);

		final boolean isClear3 = !searchState.isOpaqueCube() || searchState.getLightOpacity() == 0;

		// c = corner - values at corners of face
		int cLight0, cLight1, cLight2, cLight3;
		float cAo0, cAo1, cAo2, cAo3;
		boolean cEm0, cEm1, cEm2, cEm3;

		// If neighbors on both sides of the corner are opaque, then apparently we use the light/shade
		// from one of the sides adjacent to the corner.  If either neighbor is clear (no light subtraction)
		// then we use values from the outwardly diagonal corner. (outwardly = position is one more away from light face)
		if (!isClear2 && !isClear0) {
			cAo0 = ao0;
			cLight0 = light0;
			cEm0 = em0;
		} else {
			searchPos.set(lightPos).move(aoFace.neighbors[0]).move(aoFace.neighbors[2]);
			searchState = world.getBlock(searchPos);
			cAo0 = ao(searchPos, searchState);
			cLight0 = light(searchPos, searchState);
			cEm0 = hasEmissiveLighting(world, searchPos, searchState);
		}

		if (!isClear3 && !isClear0) {
			cAo1 = ao0;
			cLight1 = light0;
			cEm1 = em0;
		} else {
			searchPos.set(lightPos).move(aoFace.neighbors[0]).move(aoFace.neighbors[3]);
			searchState = world.getBlock(searchPos);
			cAo1 = ao(searchPos, searchState);
			cLight1 = light(searchPos, searchState);
			cEm1 = hasEmissiveLighting(world, searchPos, searchState);
		}

		if (!isClear2 && !isClear1) {
			cAo2 = ao1;
			cLight2 = light1;
			cEm2 = em1;
		} else {
			searchPos.set(lightPos).move(aoFace.neighbors[1]).move(aoFace.neighbors[2]);
			searchState = world.getBlock(searchPos);
			cAo2 = ao(searchPos, searchState);
			cLight2 = light(searchPos, searchState);
			cEm2 = hasEmissiveLighting(world, searchPos, searchState);
		}

		if (!isClear3 && !isClear1) {
			cAo3 = ao1;
			cLight3 = light1;
			cEm3 = em1;
		} else {
			searchPos.set(lightPos).move(aoFace.neighbors[1]).move(aoFace.neighbors[3]);
			searchState = world.getBlock(searchPos);
			cAo3 = ao(searchPos, searchState);
			cLight3 = light(searchPos, searchState);
			cEm3 = hasEmissiveLighting(world, searchPos, searchState);
		}

		// If on block face or neighbor isn't occluding, "center" will be neighbor brightness
		// Doesn't use light pos because logic not based solely on this block's geometry
		int lightCenter;
		boolean emCenter;
		searchPos.set(pos, lightFace);
		searchState = world.getBlock(searchPos);

		if (isOnBlockFace || !searchState.isOpaqueCube()) {
			lightCenter = light(searchPos, searchState);
			emCenter = hasEmissiveLighting(world, searchPos, searchState);
		} else {
			lightCenter = light(pos, block);
			emCenter = hasEmissiveLighting(world, pos, block);
		}

		float aoCenter = ao(lightPos, world.getBlock(lightPos));
		float worldBrightness = world.getBrightness(lightFace, shade);

		result.a0 = ((ao3 + ao0 + cAo1 + aoCenter) * 0.25F) * worldBrightness;
		result.a1 = ((ao2 + ao0 + cAo0 + aoCenter) * 0.25F) * worldBrightness;
		result.a2 = ((ao2 + ao1 + cAo2 + aoCenter) * 0.25F) * worldBrightness;
		result.a3 = ((ao3 + ao1 + cAo3 + aoCenter) * 0.25F) * worldBrightness;

		result.l0(meanBrightness(light3, light0, cLight1, lightCenter, em3, em0, cEm1, emCenter));
		result.l1(meanBrightness(light2, light0, cLight0, lightCenter, em2, em0, cEm0, emCenter));
		result.l2(meanBrightness(light2, light1, cLight2, lightCenter, em2, em1, cEm2, emCenter));
		result.l3(meanBrightness(light3, light1, cLight3, lightCenter, em3, em1, cEm3, emCenter));
	}

	public static int getLightmapCoordinates(LightDataAccess world, Block state, BlockPos pos) {
        // Same as WorldRenderer.getLightmapCoordinates but without the hasEmissiveLighting check.
        // We don't want emissive lighting to influence the minimum lightmap in a quad,
        // so when the fix is enabled we apply emissive lighting after the quad minimum is computed.
        // See AoCalculator#meanBrightness.
		// TODO: this is probably way slower than it has to be since we have the light cache
        int i = world.getWorld().getLightLevel(EnumSkyBlock.Sky, pos.getX(), pos.getY(), pos.getZ());
        int j = world.getWorld().getLightLevel(EnumSkyBlock.Block, pos.getX(), pos.getY(), pos.getZ());
        int k = state.getLightValue(); // TODO: should this use the world call?

        if (j < k) {
            j = k;
        }

        return i << 20 | j << 4;
    }

	private boolean hasEmissiveLighting(WorldSlice world, BlockPos pos, Block block) {
        // TODO: we have the world - is this the best way to check if the block is emitting light?
		return block.getLightValue() > 0;
	}

	/**
	 * Vanilla code excluded missing light values from mean but was not isotropic.
	 * Still need to substitute or edges are too dark but consistently use the min
	 * value from all four samples.
	 */
	private static int meanBrightness(int lightA, int lightB, int lightC, int lightD, boolean emA, boolean emB, boolean emC, boolean emD) {
        if (lightA == 0 || lightB == 0 || lightC == 0 || lightD == 0) {
            // Normalize values to non-zero minimum
            final int min = nonZeroMin(nonZeroMin(lightA, lightB), nonZeroMin(lightC, lightD));

            lightA = Math.max(lightA, min);
            lightB = Math.max(lightB, min);
            lightC = Math.max(lightC, min);
            lightD = Math.max(lightD, min);
        }

        // Apply the fullbright lightmap from emissive blocks at the very end so it cannot influence
        // the minimum lightmap and produce incorrect results (for example, sculk sensors in a dark room)
        if (emA) lightA = (15 << 20) | (15 << 4);
        if (emB) lightB = (15 << 20) | (15 << 4);
        if (emC) lightC = (15 << 20) | (15 << 4);
        if (emD) lightD = (15 << 20) | (15 << 4);

        return meanInnerBrightness(lightA, lightB, lightC, lightD);
    }

	private static int meanInnerBrightness(int a, int b, int c, int d) {
		// bitwise divide by 4, clamp to expected (positive) range
		return a + b + c + d >> 2 & 0xFF00FF;
	}

	private static int nonZeroMin(int a, int b) {
		if (a == 0) return b;
		if (b == 0) return a;
		return Math.min(a, b);
	}
}
