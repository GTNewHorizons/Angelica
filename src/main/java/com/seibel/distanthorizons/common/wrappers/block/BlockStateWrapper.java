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

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import cpw.mods.fml.common.registry.GameData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBeacon;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.init.Blocks;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

public class BlockStateWrapper implements IBlockStateWrapper
{
    /** example "minecraft:water" */
    public static final String RESOURCE_LOCATION_SEPARATOR = ":";
    /** example "minecraft:water_STATE_{level:0}" */
    public static final String STATE_STRING_SEPARATOR = "_STATE_";


    // must be defined before AIR, otherwise a null pointer will be thrown
    private static final Logger LOGGER = DhLoggerBuilder.getLogger();

    public static final ConcurrentHashMap<FakeBlockState, BlockStateWrapper> WRAPPER_BY_BLOCK_STATE = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, BlockStateWrapper> WRAPPER_BY_RESOURCE_LOCATION = new ConcurrentHashMap<>();

    public static final String AIR_STRING = "AIR";
    public static final BlockStateWrapper AIR = new BlockStateWrapper(null, null);

    public static final String DIRT_RESOURCE_LOCATION_STRING = "minecraft:dirt";

    public static HashSet<IBlockStateWrapper> rendererIgnoredBlocks = null;
    public static HashSet<IBlockStateWrapper> rendererIgnoredCaveBlocks = null;

    /** keep track of broken blocks so we don't log every time */
    private static final HashSet<String> BROKEN_RESOURCE_LOCATIONS = new HashSet<>();



    // properties //

    @Nullable
    public final FakeBlockState blockState;
    /** technically final, but since it requires a method call to generate it can't be marked as such */
    private String serialString;
    private final int hashCode;
    /**
     * Cached opacity value, -1 if not populated. <br>
     * Should be between {@link LodUtil#BLOCK_FULLY_OPAQUE} and {@link LodUtil#BLOCK_FULLY_OPAQUE}
     */
    private int opacity = -1;
    /** used by the Iris shader mod to determine how each LOD should be rendered */
    private byte blockMaterialId = 0;

    private final boolean isBeaconBlock;
    private final boolean isBeaconBaseBlock;
    /** null if this block can't tint beacons */
    private final Color beaconTintColor;
    private final Color mapColor;



    //==============//
    // constructors //
    //==============//

    public static BlockStateWrapper fromBlockState(FakeBlockState blockState, ILevelWrapper levelWrapper)
    {
        if (blockState == null || blockState.block == Blocks.air)
        {
            return AIR;
        }

        if (WRAPPER_BY_BLOCK_STATE.containsKey(blockState))
        {
            return WRAPPER_BY_BLOCK_STATE.get(blockState);
        }
        else
        {
            BlockStateWrapper newWrapper = new BlockStateWrapper(blockState, levelWrapper);
            WRAPPER_BY_BLOCK_STATE.put(blockState, newWrapper);
            return newWrapper;
        }
    }

    /**
     * Can be faster than {@link BlockStateWrapper#fromBlockState(FakeBlockState, ILevelWrapper)}
     * in cases where the same block state is expected to be referenced multiple times.
     */
    public static BlockStateWrapper fromBlockState(FakeBlockState blockState, ILevelWrapper levelWrapper, IBlockStateWrapper guess)
    {
        FakeBlockState guessBlockState = (guess == null || guess.isAir()) ? null : (FakeBlockState) guess.getWrappedMcObject();
        FakeBlockState inputBlockState = (blockState == null || blockState.block == Blocks.air) ? null : blockState;

        if (guess instanceof BlockStateWrapper
            && guessBlockState == inputBlockState)
        {
            return (BlockStateWrapper) guess;
        }
        else
        {
            return fromBlockState(blockState, levelWrapper);
        }
    }

    private BlockStateWrapper(FakeBlockState blockState, ILevelWrapper levelWrapper)
    {
        this.blockState = blockState;
        this.serialString = this.serialize(levelWrapper);
        this.hashCode = Objects.hash(this.serialString);
        this.blockMaterialId = this.calculateEDhApiBlockMaterialId().index;

        // beacon blocks
        String lowercaseSerial = this.serialString.toLowerCase();
        boolean isBeaconBaseBlock = false;
        for (int i = 0; i < LodUtil.BEACON_BASE_BLOCK_NAME_LIST.size(); i++)
        {
            String baseBlockName = LodUtil.BEACON_BASE_BLOCK_NAME_LIST.get(i);
            if (lowercaseSerial.contains(baseBlockName))
            {
                isBeaconBaseBlock = true;
                break;
            }
        }
        this.isBeaconBaseBlock = isBeaconBaseBlock;
        this.isBeaconBlock = lowercaseSerial.contains("minecraft:beacon");

        // beacon tint color
        Color beaconTintColor = null;
        if (this.blockState != null
            // beacon blocks also show up here, but since they block the beacon beam we don't want their color
            && !this.isBeaconBlock)
        {
            Block block = this.blockState.block;
            if (block instanceof BlockBeacon)
            {
                int colorInt;
                colorInt = block.getBlockColor();

                beaconTintColor = ColorUtil.toColorObjRGB(colorInt);
            }
        }
        this.beaconTintColor = beaconTintColor;


        int mcColor = 0;
        if (this.blockState != null)
        {
            mcColor = this.blockState.block.blockMaterial.getMaterialMapColor().colorValue;
            this.mapColor = ColorUtil.toColorObjRGB(mcColor);
        }
        else
        {
            this.mapColor = new Color(0,0,0,0);
        }

        //LOGGER.trace("Created BlockStateWrapper ["+this.serialString+"] for ["+blockState+"] with material ID ["+this.EDhApiBlockMaterialId+"]");
    }



    //====================//
    // LodBuilder methods //
    //====================//

    /**
     * Requires a {@link ILevelWrapper} since {@link BlockStateWrapper#deserialize(String,ILevelWrapper)} also requires one.
     * This way the method won't accidentally be called before the deserialization can be completed.
     */
    public static HashSet<IBlockStateWrapper> getRendererIgnoredBlocks(ILevelWrapper levelWrapper)
    {
        // use the cached version if possible
        if (rendererIgnoredBlocks != null)
        {
            return rendererIgnoredBlocks;
        }

        HashSet<String> baseIgnoredBlock = new HashSet<>();
        baseIgnoredBlock.add(AIR_STRING);
        rendererIgnoredBlocks = getBlockWrappers(Config.Client.Advanced.Graphics.Culling.ignoredRenderBlockCsv, baseIgnoredBlock, levelWrapper);
        return rendererIgnoredBlocks;
    }
    /**
     * Requires a {@link ILevelWrapper} since {@link BlockStateWrapper#deserialize(String,ILevelWrapper)} also requires one.
     * This way the method won't accidentally be called before the deserialization can be completed.
     */
    public static HashSet<IBlockStateWrapper> getRendererIgnoredCaveBlocks(ILevelWrapper levelWrapper)
    {
        // use the cached version if possible
        if (rendererIgnoredCaveBlocks != null)
        {
            return rendererIgnoredCaveBlocks;
        }

        HashSet<String> baseIgnoredBlock = new HashSet<>();
        baseIgnoredBlock.add(AIR_STRING);
        rendererIgnoredCaveBlocks = getBlockWrappers(Config.Client.Advanced.Graphics.Culling.ignoredRenderCaveBlockCsv, baseIgnoredBlock, levelWrapper);
        return rendererIgnoredCaveBlocks;
    }

    public static void clearRendererIgnoredBlocks() { rendererIgnoredBlocks = null; }
    public static void clearRendererIgnoredCaveBlocks() { rendererIgnoredCaveBlocks = null; }



    // lod builder helpers //

    private static HashSet<IBlockStateWrapper> getBlockWrappers(ConfigEntry<String> config, HashSet<String> baseResourceLocations, ILevelWrapper levelWrapper)
    {
        // get the base blocks
        HashSet<String> blockStringList = new HashSet<>();
        if (baseResourceLocations != null)
        {
            blockStringList.addAll(baseResourceLocations);
        }

        // get the config blocks
        String ignoreBlockCsv = config.get();
        if (ignoreBlockCsv != null)
        {
            blockStringList.addAll(Arrays.asList(ignoreBlockCsv.split(",")));
        }

        return getBlockWrappers(blockStringList, levelWrapper);
    }
    private static HashSet<IBlockStateWrapper> getBlockWrappers(HashSet<String> blockResourceLocationSet, ILevelWrapper levelWrapper)
    {
        // deserialize each of the given resource locations
        HashSet<IBlockStateWrapper> blockStateWrappers = new HashSet<>();
        for (String blockResourceLocation : blockResourceLocationSet)
        {
            try
            {
                if (blockResourceLocation == null)
                {
                    // shouldn't happen, but just in case
                    continue;
                }
                String cleanedResourceLocation = blockResourceLocation.trim();
                if (cleanedResourceLocation.length() == 0)
                {
                    continue;
                }


                BlockStateWrapper defaultBlockStateToIgnore = (BlockStateWrapper) deserialize(cleanedResourceLocation, levelWrapper);
                blockStateWrappers.add(defaultBlockStateToIgnore);

                if (defaultBlockStateToIgnore != AIR)
                {
                    BlockStateWrapper newBlockToIgnore = BlockStateWrapper.fromBlockState(defaultBlockStateToIgnore.blockState, levelWrapper);
                    blockStateWrappers.add(newBlockToIgnore);
                }
                else
                {
                    // air is a special case so it must be handled separately
                    blockStateWrappers.add(AIR);
                }
            }
            catch (IOException e)
            {
                LOGGER.warn("Unable to deserialize block with the resource location: ["+blockResourceLocation+"]. Error: "+e.getMessage(), e);
            }
            catch (Exception e)
            {
                LOGGER.warn("Unexpected error deserializing block with the resource location: ["+blockResourceLocation+"]. Error: "+e.getMessage(), e);
            }
        }

        return blockStateWrappers;
    }



    //=================//
    // wrapper methods //
    //=================//

    @Override
    public int getOpacity()
    {
        // use the cached opacity value if possible
        if (this.opacity != -1)
        {
            return this.opacity;
        }


        // get block properties (default to the values used by air)
        boolean canOcclude = false;
        boolean propagatesSkyLightDown = true;
        if (this.blockState != null)
        {
            canOcclude = this.blockState.block.blockMaterial.isSolid();

            propagatesSkyLightDown = this.blockState.block.getLightOpacity() == 0;
        }



        // this method isn't perfect, but works well enough for our use case
        int opacity;
        if (this.isAir())
        {
            opacity = LodUtil.BLOCK_FULLY_TRANSPARENT;
        }
        else if (this.isLiquid() && !canOcclude)
        {
            // probably not a waterlogged block (which should block light entirely)

            // +1 to indicate that the block is translucent (in between transparent and opaque)
            opacity = LodUtil.BLOCK_FULLY_TRANSPARENT + 1;
        }
        else if (propagatesSkyLightDown && !canOcclude)
        {
            // probably glass or some other fully transparent block

            // !canOcclude is required to ignore stairs and slabs since
            // propagateSkyLightDown is true for them, but they're solid and don't actually let light through

            opacity = LodUtil.BLOCK_FULLY_TRANSPARENT;
        }
        else
        {
            // default for all other blocks
            opacity = LodUtil.BLOCK_FULLY_OPAQUE;
        }


        this.opacity = opacity;
        return this.opacity;
    }

    @Override
    public int getLightEmission() { return (this.blockState != null) ? this.blockState.block.getLightValue() : 0; }

    @Override
    public String getSerialString() { return this.serialString; }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (obj == null || this.getClass() != obj.getClass())
        {
            return false;
        }

        BlockStateWrapper that = (BlockStateWrapper) obj;
        // the serialized value is used so we can test the contents instead of the references
        return Objects.equals(this.getSerialString(), that.getSerialString());
    }

    @Override
    public int hashCode() { return this.hashCode; }


    @Override
    public Object getWrappedMcObject() { return this.blockState; }

    @Override
    public boolean isAir() { return this.isAir(this.blockState); }
    public boolean isAir(FakeBlockState blockState) { return blockState == null || blockState.block == Blocks.air; }

    @Override
    public boolean isSolid()
    {
        if (this.isAir())
        {
            return false;
        }

        return this.blockState.block.blockMaterial.isSolid();
    }

    @Override
    public boolean isLiquid()
    {
        if (this.isAir())
        {
            return false;
        }

        return this.blockState.block.blockMaterial.isLiquid();
    }

    @Override
    public boolean isBeaconBlock() { return this.isBeaconBlock; }
    @Override
    public boolean isBeaconBaseBlock() { return this.isBeaconBaseBlock; }
    @Override
    public boolean isBeaconTintBlock() { return this.beaconTintColor != null; }

    @Override
    public Color getMapColor() { return this.mapColor; }
    @Override
    public Color getBeaconTintColor() { return this.beaconTintColor; }

    @Override
    public byte getMaterialId() { return this.blockMaterialId; }

    @Override
    public String toString() { return this.getSerialString(); }



    //=======================//
    // serialization methods //
    //=======================//

    private String serialize(ILevelWrapper levelWrapper)
    {
        if (this.blockState == null)
        {
            return AIR_STRING;
        }


        String id = GameData.getBlockRegistry().getNameForObject(this.blockState.block);

        this.serialString = id + STATE_STRING_SEPARATOR + this.blockState.meta;

        return this.serialString;
    }


    /** will only work if a level is currently loaded */
    public static IBlockStateWrapper deserialize(String resourceStateString, ILevelWrapper levelWrapper) throws IOException
    {
        // we need the final string for the concurrent hash map later
        final String finalResourceStateString = resourceStateString;

        if (finalResourceStateString.equals(AIR_STRING) || finalResourceStateString.equals("")) // the empty string shouldn't normally happen, but just in case
        {
            return AIR;
        }

        // attempt to use the existing wrapper
        if (WRAPPER_BY_RESOURCE_LOCATION.containsKey(finalResourceStateString))
        {
            return WRAPPER_BY_RESOURCE_LOCATION.get(finalResourceStateString);
        }



        // if no wrapper is found, default to air
        BlockStateWrapper foundWrapper = AIR;
        try
        {
            // try to parse out the BlockState
            String blockStatePropertiesString = null; // will be null if no properties were included
            int stateSeparatorIndex = resourceStateString.indexOf(STATE_STRING_SEPARATOR);
            if (stateSeparatorIndex != -1)
            {
                // blockstate properties found
                blockStatePropertiesString = resourceStateString.substring(stateSeparatorIndex + STATE_STRING_SEPARATOR.length());
                resourceStateString = resourceStateString.substring(0, stateSeparatorIndex);
            }


            // attempt to get the BlockState from all possible BlockStates
            try
            {
                Block block = GameData.getBlockRegistry().getObject(resourceStateString);
                int meta = 0;
                if (blockStatePropertiesString != null)
                {
                    meta = Integer.parseInt(blockStatePropertiesString);
                }


                foundWrapper = new BlockStateWrapper(new FakeBlockState(block, meta), levelWrapper);
                return foundWrapper;
            }
            catch (Exception e)
            {
                throw new IOException("Failed to deserialize the string [" + finalResourceStateString + "] into a BlockStateWrapper: " + e.getMessage(), e);
            }
        }
        finally
        {
            // put if absent in case two threads deserialize at the same time
            // unfortunately we can't put everything in a computeIfAbsent() since we also throw exceptions
            WRAPPER_BY_RESOURCE_LOCATION.putIfAbsent(finalResourceStateString, foundWrapper);
        }
    }


    //==============//
    // Iris methods //
    //==============//

    private EDhApiBlockMaterial calculateEDhApiBlockMaterialId()
    {
        if (this.blockState == null)
        {
            return EDhApiBlockMaterial.AIR;
        }

        String serialString = this.getSerialString().toLowerCase();

        if (this.blockState.block instanceof BlockLeavesBase
            || serialString.contains("bamboo")
            || serialString.contains("cactus")
            || serialString.contains("chorus_flower")
            || serialString.contains("mushroom")
        )
        {
            return EDhApiBlockMaterial.LEAVES;
        }
        else if (this.blockState.block == Blocks.lava || this.blockState.block == Blocks.flowing_lava)
        {
            return EDhApiBlockMaterial.LAVA;
        }
        else if (this.isLiquid() || this.blockState.block == Blocks.water || this.blockState.block == Blocks.flowing_water)
        {
            return EDhApiBlockMaterial.WATER;
        }
        else if (this.blockState.block.stepSound == Block.soundTypeWood
            || serialString.contains("root")
				)
        {
            return EDhApiBlockMaterial.WOOD;
        }
		else if (this.blockState.block.stepSound == Block.soundTypeMetal
				)
        {
            return EDhApiBlockMaterial.METAL;
        }
		else if (this.blockState.block instanceof BlockGrass)
    {
        return EDhApiBlockMaterial.GRASS;
    }
    else if (
        serialString.contains("dirt")
            || serialString.contains("gravel")
            || serialString.contains("mud")
            || serialString.contains("podzol")
            || serialString.contains("mycelium")
    )
    {
        return EDhApiBlockMaterial.DIRT;
    }
    else if (this.serialString.contains("snow"))
    {
        return EDhApiBlockMaterial.SNOW;
    }
    else if (serialString.contains("sand"))
    {
        return EDhApiBlockMaterial.SAND;
    }
    else if (serialString.contains("terracotta"))
    {
        return EDhApiBlockMaterial.TERRACOTTA;
    }
    else if (this.blockState.block == Blocks.netherrack || this.blockState.block == Blocks.nether_brick)
    {
        return EDhApiBlockMaterial.NETHER_STONE;
    }
    else if (serialString.contains("stone")
        || serialString.contains("ore"))
    {
        return EDhApiBlockMaterial.STONE;
    }
    else if (this.blockState.block.getLightValue() > 0)
    {
        return EDhApiBlockMaterial.ILLUMINATED;
    }
    else
    {
        return EDhApiBlockMaterial.UNKNOWN;
    }
    }

}
