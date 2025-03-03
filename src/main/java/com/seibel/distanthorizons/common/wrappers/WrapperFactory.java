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

package com.seibel.distanthorizons.common.wrappers;

import com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.interfaces.factories.IDhApiWrapperFactory;
import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.level.IDhServerLevel;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.worldGeneration.AbstractBatchGenerationEnvironmentWrapper;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.io.IOException;
import java.util.HashSet;

/**
 * This handles creating abstract wrapper objects.
 */
public class WrapperFactory implements IWrapperFactory
{
	public static final WrapperFactory INSTANCE = new WrapperFactory();



	//==============//
	// core methods //
	//==============//

	@Override
	public AbstractBatchGenerationEnvironmentWrapper createBatchGenerator(IDhLevel targetLevel)
	{
		if (targetLevel instanceof IDhServerLevel)
		{
			return new BatchGenerationEnvironment((IDhServerLevel) targetLevel);
		}
		else
		{
			throw new IllegalArgumentException("The target level must be a server-side level.");
		}
	}

	@Override
	public IDhApiBiomeWrapper getBiomeWrapper(String resourceLocationString, IDhApiLevelWrapper levelWrapper) throws IOException, ClassCastException
	{
		if (!(levelWrapper instanceof ILevelWrapper))
		{
			throw new ClassCastException("levelWrapper must be returned by DH and of type ["+ILevelWrapper.class.getName()+"].");
		}

		return BiomeWrapper.deserialize(resourceLocationString, (ILevelWrapper)levelWrapper);
	}
	@Override
	public IDhApiBlockStateWrapper getDefaultBlockStateWrapper(String resourceLocationString, IDhApiLevelWrapper levelWrapper) throws IOException, ClassCastException
	{
		if (!(levelWrapper instanceof ILevelWrapper))
		{
			throw new ClassCastException("Invalid ["+IDhApiLevelWrapper.class.getSimpleName()+"] value given. Level wrapper object must be one given by the DH API (it can't be a custom implementation), specifically of type ["+ILevelWrapper.class.getName()+"].");
		}

		return BlockStateWrapper.deserialize(resourceLocationString, (ILevelWrapper)levelWrapper);
	}

	@Override
	public IBiomeWrapper deserializeBiomeWrapper(String str, ILevelWrapper levelWrapper) throws IOException { return BiomeWrapper.deserialize(str, levelWrapper); }
	@Override
	public IBiomeWrapper getPlainsBiomeWrapper(ILevelWrapper levelWrapper) // TODO is there a way we could get this without the levelWrapper? it isn't necessary but would clean up the code a bit
	{
		try
		{
			return BiomeWrapper.deserialize(BiomeWrapper.PLAINS_RESOURCE_LOCATION_STRING, levelWrapper);
		}
		catch (IOException e)
		{
			throw new LodUtil.AssertFailureException("Unable to parse plains resource string ["+BiomeWrapper.PLAINS_RESOURCE_LOCATION_STRING+"], error:\n " + e.getMessage());
		}
	}

	@Override
	public IBlockStateWrapper deserializeBlockStateWrapper(String str, ILevelWrapper levelWrapper) throws IOException { return BlockStateWrapper.deserialize(str, levelWrapper); }
	@Override
	public IBlockStateWrapper getAirBlockStateWrapper() { return BlockStateWrapper.AIR; }

	@Override
	public HashSet<IBlockStateWrapper> getRendererIgnoredBlocks(ILevelWrapper levelWrapper) { return BlockStateWrapper.getRendererIgnoredBlocks(levelWrapper); }
	@Override
	public HashSet<IBlockStateWrapper> getRendererIgnoredCaveBlocks(ILevelWrapper levelWrapper) { return BlockStateWrapper.getRendererIgnoredCaveBlocks(levelWrapper); }

	@Override
	public void resetRendererIgnoredCaveBlocks() { BlockStateWrapper.clearRendererIgnoredCaveBlocks(); }
	@Override
	public void resetRendererIgnoredBlocksSet() { BlockStateWrapper.clearRendererIgnoredBlocks(); }


	/**
	 * Note: when this is updated for different MC versions, make sure you also update the documentation in
	 * {@link IDhApiWorldGenerator#generateChunks} and the type list in {@link WrapperFactory#createChunkWrapperErrorMessage}. <br><br>
	 *
	 * For full method documentation please see: {@link IWrapperFactory#createChunkWrapper}
	 *
	 * @see IWrapperFactory#createChunkWrapper
	 */
	public IChunkWrapper createChunkWrapper(Object[] objectArray) throws ClassCastException
	{
		if (objectArray.length == 1 && objectArray[0] instanceof IChunkWrapper)
		{
			try
			{
				// this path should only happen when called by Distant Horizons code
				// API implementors should never hit this path
				return (IChunkWrapper) objectArray[0];
			}
			catch (Exception e)
			{
				throw new ClassCastException(createChunkWrapperErrorMessage(objectArray));
			}
		}

		//#if MC_VER <= MC_1_XX_X
		else if (objectArray.length == 2)
		{
			// correct number of parameters from the API

			// chunk
			if (!(objectArray[0] instanceof Chunk))
			{
				throw new ClassCastException(createChunkWrapperErrorMessage(objectArray));
			}
            Chunk chunk = (Chunk) objectArray[0];

			// level / light source
			if (!(objectArray[1] instanceof World))
			{
				throw new ClassCastException(createChunkWrapperErrorMessage(objectArray));
			}
			// the level is needed for the DH level wrapper...
            World level = (World) objectArray[1];


			// level wrapper
			ILevelWrapper levelWrapper = !level.isRemote
					? ClientLevelWrapper.getWrapper((WorldClient) level)
					: ServerLevelWrapper.getWrapper((WorldServer) level);


			return new ChunkWrapper(chunk, levelWrapper);
		}
		// incorrect number of parameters from the API
		else
		{
			throw new ClassCastException(createChunkWrapperErrorMessage(objectArray));
		}
		//#endif
	}
	/**
	 * Note: when this is updated for different MC versions,
	 * make sure you also update the documentation in {@link IDhApiWorldGenerator#generateChunks}.
	 */
	private static String createChunkWrapperErrorMessage(Object[] objectArray)
	{
		String[] expectedClassNames;

		//#if MC_VER <= MC_1_XX_X
		expectedClassNames = new String[]
		{
			ChunkAccess.class.getName(),
			"[ServerLevel] or [ClientLevel]" // Classes are not referenced by names to avoid exception when one of them is missing
		};
		//#endif

		return createWrapperErrorMessage("Chunk wrapper", expectedClassNames, objectArray);
	}



	//=============//
	// api methods //
	//=============//

	// documentation should be in the API interface

	public IDhApiBiomeWrapper getBiomeWrapper(Object[] objectArray, IDhApiLevelWrapper levelWrapper)
	{
		// confirm the API level wrapper is also a Core wrapper
		if (!(levelWrapper instanceof ILevelWrapper))
		{
			throw new ClassCastException("Invalid ["+IDhApiLevelWrapper.class.getSimpleName()+"] value given. Level wrapper object must be one given by the DH API (it can't be a custom implementation), specifically of type ["+ILevelWrapper.class.getName()+"].");
		}
		ILevelWrapper coreLevelWrapper = (ILevelWrapper) levelWrapper;



		#if MC_VER < MC_1_20_4
		if (objectArray.length != 1)
		{
			throw new ClassCastException(createBiomeWrapperErrorMessage(objectArray));
		}
		#endif

		#if MC_VER < MC_1_18_2
		if (!(objectArray[0] instanceof Biome))
		{
			throw new ClassCastException(createBiomeWrapperErrorMessage(objectArray));
		}

		Biome biome = (Biome) objectArray[0];
		return BiomeWrapper.getBiomeWrapper(biome, coreLevelWrapper);
		#else
		if (!(objectArray[0] instanceof Holder) || !(((Holder<?>) objectArray[0]).value() instanceof Biome))
		{
			throw new ClassCastException(createBiomeWrapperErrorMessage(objectArray));
		}

		Holder<Biome> biomeHolder = (Holder<Biome>) objectArray[0];
		return BiomeWrapper.getBiomeWrapper(biomeHolder, coreLevelWrapper);
		#endif
	}
	/**
	 * Note: when this is updated for different MC versions,
	 * make sure you also update the documentation in {@link IDhApiWrapperFactory#getBiomeWrapper}.
	 */
	private static String createBiomeWrapperErrorMessage(Object[] objectArray)
	{
		String[] expectedClassNames;

		#if MC_VER < MC_1_18_2
		expectedClassNames = new String[] { Biome.class.getName() };
		#else
		expectedClassNames = new String[] { Holder.class.getName()+"<"+Biome.class.getName()+">" };
		#endif

		return createWrapperErrorMessage("Biome wrapper", expectedClassNames, objectArray);
	}

	public IDhApiBlockStateWrapper getBlockStateWrapper(Object[] objectArray, IDhApiLevelWrapper levelWrapper)
	{
		// confirm the API level wrapper is also a Core wrapper
		if (!(levelWrapper instanceof ILevelWrapper))
		{
			throw new ClassCastException("Invalid ["+IDhApiLevelWrapper.class.getSimpleName()+"] value given. Level wrapper object must be one given by the DH API (it can't be a custom implementation), specifically of type ["+ILevelWrapper.class.getName()+"].");
		}
		ILevelWrapper coreLevelWrapper = (ILevelWrapper) levelWrapper;



		//#if MC_VER <= MC_1_XX_X
		if (objectArray.length != 1)
		{
			throw new ClassCastException(createBlockStateWrapperErrorMessage(objectArray));
		}
		if (!(objectArray[0] instanceof BlockState))
		{
			throw new ClassCastException(createBlockStateWrapperErrorMessage(objectArray));
		}

		BlockState blockState = (BlockState) objectArray[0];
		return BlockStateWrapper.fromBlockState(blockState, coreLevelWrapper);
		//#endif
	}
	/**
	 * Note: when this is updated for different MC versions,
	 * make sure you also update the documentation in {@link IDhApiWrapperFactory#getBlockStateWrapper}.
	 */
	private static String createBlockStateWrapperErrorMessage(Object[] objectArray)
	{
		String[] expectedClassNames;

		#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
		expectedClassNames = new String[] { Biome.class.getName() };
		#else
		expectedClassNames = new String[] { Holder.class.getName()+"<"+Biome.class.getName()+">" };
		#endif

		return createWrapperErrorMessage("BlockState wrapper", expectedClassNames, objectArray);
	}




	//================//
	// helper methods //
	//================//

	private static String createWrapperErrorMessage(String wrapperName, String[] expectedClassNames, Object[] objectArray)
	{
		// error header
		StringBuilder message = new StringBuilder(
				wrapperName + " creation failed. \n" +
						"Expected object array parameters: \n");


		// expected parameters
		for (String expectedClassName : expectedClassNames)
		{
			message.append("[").append(expectedClassName).append("], \n");
		}


		// given parameters
		if (objectArray.length != 0)
		{
			message.append("Given parameters: ");
			for (Object obj : objectArray)
			{
				String objClassName = (obj != null) ? obj.getClass().getName() : "NULL";
				message.append("[").append(objClassName).append("], ");
			}
		}
		else
		{
			message.append(" No parameters given.");
		}


		return message.toString();
	}

}
