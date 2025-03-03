/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.common.wrappers.block;

import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
#if MC_VER >= MC_1_19_2
import net.minecraft.util.RandomSource;
#else
import java.util.Random;
#endif
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This stores and calculates the colors
 * the given {@link BlockState} should have based
 * on the given {@link IClientLevelWrapper}.
 * 
 * @see ColorUtil
 */
public class ClientBlockStateColorCache
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	private static final HashSet<BlockState> BLOCK_STATES_THAT_NEED_LEVEL = new HashSet<>();
	private static final HashSet<BlockState> BROKEN_BLOCK_STATES = new HashSet<>();
	
	/** 
	 * Methods using MC's "RandomSource" object aren't thread safe <br>
	 * so we need to put locks around that logic. <br>
	 * specifically:
	 * <code>
	 * getBlockModel(this.blockState).getQuads(this.blockState, direction, RANDOM)
	 * </code>
	 */
	private static final ReentrantLock RESOLVE_LOCK = new ReentrantLock();
	
	
	/** This is the order each direction on a block is processed when attempting to get the texture/color */
	private static final Direction[] COLOR_RESOLUTION_DIRECTION_ORDER = { Direction.UP, Direction.NORTH, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.DOWN };
	
	private static final int FLOWER_COLOR_SCALE = 5;
	
	
	
	#if MC_VER < MC_1_19_2
	private static final Random RANDOM = new Random(0);
	#else
	/** Note: this object isn't thread safe and must be put in a lock */
	private static final RandomSource RANDOM = RandomSource.create();
	#endif
	
	private final IClientLevelWrapper levelWrapper;
	private final BlockState blockState;
	private final LevelReader level;
	
	private boolean isColorResolved = false;
	private int baseColor = 0;
	private boolean needShade = true;
	private boolean needPostTinting = false;
	private int tintIndex = 0;
	
	
	
	//===========//
	// constants //
	//===========//
	
	private static final int MIN_SRGB_BITS = 0x39000000; // 2^(-13)
	private static final int MAX_SRGB_BITS = 0x3f7fffff; // 1.0 - f32::EPSILON
	private static final float MIN_SRGB_BOUND = Float.intBitsToFloat(MIN_SRGB_BITS);
	private static final float MAX_SRGB_BOUND = Float.intBitsToFloat(MAX_SRGB_BITS);
	
	private static final int[] linearToSrgbTable = new int[] 
		{
			0x0073000d, 0x007a000d, 0x0080000d, 0x0087000d, 0x008d000d, 0x0094000d, 0x009a000d, 0x00a1000d,
			0x00a7001a, 0x00b4001a, 0x00c1001a, 0x00ce001a, 0x00da001a, 0x00e7001a, 0x00f4001a, 0x0101001a,
			0x010e0033, 0x01280033, 0x01410033, 0x015b0033, 0x01750033, 0x018f0033, 0x01a80033, 0x01c20033,
			0x01dc0067, 0x020f0067, 0x02430067, 0x02760067, 0x02aa0067, 0x02dd0067, 0x03110067, 0x03440067,
			0x037800ce, 0x03df00ce, 0x044600ce, 0x04ad00ce, 0x051400ce, 0x057b00c5, 0x05dd00bc, 0x063b00b5,
			0x06970158, 0x07420142, 0x07e30130, 0x087b0120, 0x090b0112, 0x09940106, 0x0a1700fc, 0x0a9500f2,
			0x0b0f01cb, 0x0bf401ae, 0x0ccb0195, 0x0d950180, 0x0e56016e, 0x0f0d015e, 0x0fbc0150, 0x10630143,
			0x11070264, 0x1238023e, 0x1357021d, 0x14660201, 0x156601e9, 0x165a01d3, 0x174401c0, 0x182401af,
			0x18fe0331, 0x1a9602fe, 0x1c1502d2, 0x1d7e02ad, 0x1ed4028d, 0x201a0270, 0x21520256, 0x227d0240,
			0x239f0443, 0x25c003fe, 0x27bf03c4, 0x29a10392, 0x2b6a0367, 0x2d1d0341, 0x2ebe031f, 0x304d0300,
			0x31d105b0, 0x34a80555, 0x37520507, 0x39d504c5, 0x3c37048b, 0x3e7c0458, 0x40a8042a, 0x42bd0401,
			0x44c20798, 0x488e071e, 0x4c1c06b6, 0x4f76065d, 0x52a50610, 0x55ac05cc, 0x5892058f, 0x5b590559,
			0x5e0c0a23, 0x631c0980, 0x67db08f6, 0x6c55087f, 0x70940818, 0x74a007bd, 0x787d076c, 0x7c330723,
		};
	
	private static final float[] srgbToLinearTable = new float[] 
		{
			0.0f, 0.000303527f, 0.000607054f, 0.00091058103f, 0.001214108f, 0.001517635f, 0.0018211621f, 0.002124689f,
			0.002428216f, 0.002731743f, 0.00303527f, 0.0033465356f, 0.003676507f, 0.004024717f, 0.004391442f,
			0.0047769533f, 0.005181517f, 0.0056053917f, 0.0060488326f, 0.006512091f, 0.00699541f, 0.0074990317f,
			0.008023192f, 0.008568125f, 0.009134057f, 0.009721218f, 0.010329823f, 0.010960094f, 0.011612245f,
			0.012286487f, 0.012983031f, 0.013702081f, 0.014443844f, 0.015208514f, 0.015996292f, 0.016807375f,
			0.017641952f, 0.018500218f, 0.019382361f, 0.020288562f, 0.02121901f, 0.022173883f, 0.023153365f,
			0.02415763f, 0.025186857f, 0.026241222f, 0.027320892f, 0.028426038f, 0.029556843f, 0.03071345f, 0.03189604f,
			0.033104774f, 0.03433981f, 0.035601325f, 0.036889452f, 0.038204376f, 0.039546248f, 0.04091521f, 0.042311423f,
			0.043735042f, 0.045186214f, 0.046665095f, 0.048171833f, 0.049706575f, 0.051269468f, 0.052860655f, 0.05448028f,
			0.056128494f, 0.057805434f, 0.05951124f, 0.06124607f, 0.06301003f, 0.06480328f, 0.06662595f, 0.06847818f,
			0.07036011f, 0.07227186f, 0.07421358f, 0.07618539f, 0.07818743f, 0.08021983f, 0.082282715f, 0.084376216f,
			0.086500466f, 0.088655606f, 0.09084173f, 0.09305898f, 0.095307484f, 0.09758736f, 0.09989874f, 0.10224175f,
			0.10461649f, 0.10702311f, 0.10946172f, 0.111932434f, 0.11443538f, 0.116970696f, 0.11953845f, 0.12213881f,
			0.12477186f, 0.12743773f, 0.13013652f, 0.13286836f, 0.13563336f, 0.13843165f, 0.14126332f, 0.1441285f,
			0.1470273f, 0.14995982f, 0.15292618f, 0.1559265f, 0.15896086f, 0.16202943f, 0.16513224f, 0.16826946f,
			0.17144115f, 0.17464745f, 0.17788847f, 0.1811643f, 0.18447503f, 0.1878208f, 0.19120172f, 0.19461787f,
			0.19806935f, 0.2015563f, 0.20507877f, 0.2086369f, 0.21223079f, 0.21586053f, 0.21952623f, 0.22322798f,
			0.22696589f, 0.23074007f, 0.23455065f, 0.23839766f, 0.2422812f, 0.2462014f, 0.25015837f, 0.25415218f,
			0.2581829f, 0.26225072f, 0.26635566f, 0.27049786f, 0.27467737f, 0.27889434f, 0.2831488f, 0.2874409f,
			0.2917707f, 0.29613832f, 0.30054384f, 0.30498737f, 0.30946895f, 0.31398875f, 0.31854683f, 0.32314324f,
			0.32777813f, 0.33245158f, 0.33716366f, 0.34191445f, 0.3467041f, 0.3515327f, 0.35640025f, 0.36130688f,
			0.3662527f, 0.37123778f, 0.37626222f, 0.3813261f, 0.38642952f, 0.39157256f, 0.3967553f, 0.40197787f,
			0.4072403f, 0.4125427f, 0.41788515f, 0.42326775f, 0.42869055f, 0.4341537f, 0.43965724f, 0.44520125f,
			0.45078585f, 0.45641106f, 0.46207705f, 0.46778384f, 0.47353154f, 0.47932023f, 0.48514998f, 0.4910209f,
			0.49693304f, 0.5028866f, 0.50888145f, 0.5149178f, 0.5209957f, 0.52711535f, 0.5332766f, 0.5394797f,
			0.5457247f, 0.5520116f, 0.5583406f, 0.5647117f, 0.57112503f, 0.57758063f, 0.5840786f, 0.590619f, 0.597202f,
			0.60382754f, 0.61049575f, 0.61720675f, 0.62396055f, 0.63075733f, 0.637597f, 0.6444799f, 0.6514058f,
			0.65837497f, 0.66538745f, 0.67244333f, 0.6795426f, 0.68668544f, 0.69387203f, 0.70110214f, 0.70837605f,
			0.7156938f, 0.72305536f, 0.730461f, 0.7379107f, 0.7454045f, 0.75294244f, 0.76052475f, 0.7681514f, 0.77582246f,
			0.78353804f, 0.79129815f, 0.79910296f, 0.8069525f, 0.8148468f, 0.822786f, 0.8307701f, 0.83879924f, 0.84687346f,
			0.8549928f, 0.8631574f, 0.87136734f, 0.8796226f, 0.8879232f, 0.89626956f, 0.90466136f, 0.913099f, 0.92158204f,
			0.93011117f, 0.9386859f, 0.9473069f, 0.9559735f, 0.9646866f, 0.9734455f, 0.98225087f, 0.9911022f, 1.0f
		};
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ClientBlockStateColorCache(BlockState blockState, IClientLevelWrapper samplingLevel)
	{
		this.blockState = blockState;
		this.levelWrapper = samplingLevel;
		this.level = (LevelReader) samplingLevel.getWrappedMcObject();
		this.resolveColors();
	}
	
	
	
	//===================//
	// color calculation //
	//===================//
	
	private void resolveColors()
	{
		if (this.isColorResolved)
		{
			return;
		}
		
		try
		{
			// getQuads() isn't thread safe so we need to put this logic in a lock
			RESOLVE_LOCK.lock();
			
			if (this.blockState.getFluidState().isEmpty())
			{
				// look for the first non-empty direction
				List<BakedQuad> quads = null;
				for (Direction direction : COLOR_RESOLUTION_DIRECTION_ORDER)
				{
					quads = Minecraft.getInstance().getModelManager().getBlockModelShaper().
							getBlockModel(this.blockState).getQuads(this.blockState, direction, RANDOM);
					
					if (quads != null && !quads.isEmpty()
						&& !(
							this.blockState.getBlock() instanceof RotatedPillarBlock
							&& direction == Direction.UP
							)
						)
					{
						break;
					}
				}
				
				if (quads == null || quads.isEmpty())
				{
					quads = Minecraft.getInstance().getModelManager().getBlockModelShaper().
							getBlockModel(this.blockState).getQuads(this.blockState, null, RANDOM);
				}
				
				if (quads != null && !quads.isEmpty())
				{
					this.needPostTinting = quads.get(0).isTinted();
					this.needShade = quads.get(0).isShade();
					this.tintIndex = quads.get(0).getTintIndex();
					this.baseColor = calculateColorFromTexture(
	                        #if MC_VER < MC_1_17_1 quads.get(0).sprite,
							#else quads.get(0).getSprite(), #endif
							ColorMode.getColorMode(this.blockState.getBlock()));
				}
				else
				{
					// Backup method.
					this.needPostTinting = false;
					this.needShade = false;
					this.tintIndex = 0;
					this.baseColor = calculateColorFromTexture(Minecraft.getInstance().getModelManager().getBlockModelShaper().getParticleIcon(this.blockState),
							ColorMode.getColorMode(this.blockState.getBlock()));
				}
			}
			else
			{
				// Liquid Block
				this.needPostTinting = true;
				this.needShade = false;
				this.tintIndex = 0;
				this.baseColor = calculateColorFromTexture(Minecraft.getInstance().getModelManager().getBlockModelShaper().getParticleIcon(this.blockState),
						ColorMode.getColorMode(this.blockState.getBlock()));
			}
			
			this.isColorResolved = true;
		}
		finally
		{
			RESOLVE_LOCK.unlock();
		}
	}
	//TODO: Perhaps make this not just use the first frame?
	private static int calculateColorFromTexture(TextureAtlasSprite texture, ColorMode colorMode)
	{
		int count = 0;
		int alpha = 0;
		double red = 0;
		double green = 0;
		double blue = 0;
		int tempColor;
		
		// don't render Chiseled blocks.
		// Since ColorMode is set per block, you only need to check this once.
		if (colorMode != ColorMode.Chisel)
		{
			// textures normally use u and v instead of x and y
			for (int v = 0; v < getTextureHeight(texture); v++)
			{
				for (int u = 0; u < getTextureWidth(texture); u++)
				{
					//note: Minecraft color format is: 0xAA BB GG RR
					//________ DH mod color format is: 0xAA RR GG BB
					//OpenGL RGBA format native order: 0xRR GG BB AA
					//_ OpenGL RGBA format Java Order: 0xAA BB GG RR
					tempColor = TextureAtlasSpriteWrapper.getPixelRGBA(texture, 0, u, v);
					
					int r = (tempColor & 0x000000FF);
					int g = (tempColor & 0x0000FF00) >>> 8;
					int b = (tempColor & 0x00FF0000) >>> 16;
					int a = (tempColor & 0xFF000000) >>> 24;
					int scale = 1;
					if (colorMode == ColorMode.Leaves)
					{
						//switch (//FIXME add config option)
						//	case BLACK:
						//	a = 255; //simulate black background of fast leaves
						//		break;
						//	case IGNORE:
							if (a == 0) {
								continue; //same long grass
							}
							else
							{
								a = 255; //just in case there are semi transparent pixels
							}					
						//		break;
						//	case TRANSPARENT:
						//		break; //do nothing, let it count towards transparency
						
					}
					else if (a == 0 && colorMode != ColorMode.Glass)
					{
						continue;
					}
					else if (colorMode == ColorMode.Flower && (g + 25 < b || g + 25 < r))
					{
						scale = FLOWER_COLOR_SCALE;
					}
					count += scale;
					//apparently alpha is linear
					alpha += a * scale;
					//gamma correction is complicated
					red += srgbToLinearTable[r] * a * scale;
					green += srgbToLinearTable[g] * a * scale;
					blue += srgbToLinearTable[b] * a * scale;
				}
			}
		}
		
		if (count == 0)
		{
			// this block is entirely transparent
			tempColor = ColorUtil.argbToInt(0, 255, 255, 255);
		}
		else
		{
			// determine the average color
			tempColor = ColorUtil.argbToInt(
					alpha / count,
					linearToSrgb((float) (red / (double) alpha)),
					linearToSrgb((float) (green / (double) alpha)),
					linearToSrgb((float) (blue / (double) alpha)));
		}
		
		//check if not missing texture
		if (tempColor == ColorUtil.argbToInt(255, 182, 0, 182))
		{
			//make it not render at all
			tempColor = ColorUtil.argbToInt(0, 255, 255, 255);
		}
		return tempColor;
	}
	private static int getTextureWidth(TextureAtlasSprite texture)
	{
        #if MC_VER < MC_1_19_4
		return texture.getWidth();
        #else
		return texture.contents().width();
        #endif
	}
	private static int getTextureHeight(TextureAtlasSprite texture)
	{
        #if MC_VER < MC_1_19_4
		return texture.getHeight();
        #else
		return texture.contents().height();
        #endif
	}
	/**
	 * This method was suggested by IMS from the Iris/Sodium team. 
	 * That's where the numbers and code are based.
	 */
	private static int linearToSrgb(float c)
	{
		if (!(c > MIN_SRGB_BOUND)) {
			c = MIN_SRGB_BOUND;
		}
		
		if (c > MAX_SRGB_BOUND) {
			c = MAX_SRGB_BOUND;
		}
		int inputBits = Float.floatToRawIntBits(c);
		int entry = linearToSrgbTable[((inputBits - MIN_SRGB_BITS) >> 20)];
		
		int bias = (entry >>> 16) << 9;
		int scale = entry & 0xffff;
		int t = (inputBits >>> 12) & 0xff;
		
		return (bias + (scale * t)) >>> 16;
	}
	
	
	
	//===============//
	// public getter //
	//===============//
	
	public int getColor(BiomeWrapper biome, DhBlockPos pos)
	{
		// only get the tint if the block needs to be tinted
		if (!this.needPostTinting)
		{
			return this.baseColor;
		}
		
		// don't try tinting blocks that don't support our method of tint getting
		if (BROKEN_BLOCK_STATES.contains(this.blockState))
		{
			return this.baseColor;
		}
		
		
		// attempt to get the tint
		int tintColor = -1;
		try
		{
			// try to use the fast tint getter logic first
			if (!BLOCK_STATES_THAT_NEED_LEVEL.contains(this.blockState))
			{
				try
				{
					tintColor = Minecraft.getInstance().getBlockColors()
							.getColor(this.blockState, new TintWithoutLevelOverrider(biome, this.levelWrapper), McObjectConverter.Convert(pos), this.tintIndex);
				}
				catch (UnsupportedOperationException e)
				{
					// this exception generally occurs if the tint requires other blocks besides itself
					LOGGER.debug("Unable to use ["+TintWithoutLevelOverrider.class.getSimpleName()+"] to get the block tint for block: [" + this.blockState + "] and biome: [" + biome + "] at pos: " + pos + ". Error: [" + e.getMessage() + "]. Attempting to use backup method...", e);
					BLOCK_STATES_THAT_NEED_LEVEL.add(this.blockState);
				}
			}
			
			// use the level logic only if requested
			if (BLOCK_STATES_THAT_NEED_LEVEL.contains(this.blockState))
			{
				// this logic can't be used all the time due to it breaking some blocks tinting
				// specifically oceans don't render correctly
				tintColor = Minecraft.getInstance().getBlockColors()
						.getColor(this.blockState, new TintGetterOverrideFast(this.level), McObjectConverter.Convert(pos), this.tintIndex);
			}
		}
		catch (Exception e)
		{
			// only display the error once per block/biome type to reduce log spam
			if (!BROKEN_BLOCK_STATES.contains(this.blockState))
			{
				LOGGER.warn("Failed to get block color for block: [" + this.blockState + "] and biome: [" + biome + "] at pos: " + pos + ". Error: ["+e.getMessage() + "]. Note: future errors for this block/biome will be ignored.", e);
				BROKEN_BLOCK_STATES.add(this.blockState);
			}
		}
		
		
		
		if (tintColor != -1)
		{
			return ColorUtil.multiplyARGBwithRGB(this.baseColor, tintColor);
		}
		else
		{
			// unable to get the tinted color, use the base color instead
			return this.baseColor;
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	enum ColorMode
	{
		Default,
		Flower,
		Leaves,
		Chisel,
		Glass;
		
		static ColorMode getColorMode(Block block)
		{
			if (block instanceof LeavesBlock) return Leaves;
			if (block instanceof FlowerBlock) return Flower;
			if (block.toString().contains("glass")) return Glass;
			if (block.toString().equals("Block{chiselsandbits:chiseled}")) return Chisel;
			return Default;
		}
	}
	
	
	
}
