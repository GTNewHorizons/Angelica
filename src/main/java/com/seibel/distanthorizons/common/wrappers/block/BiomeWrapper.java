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

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;


/** This class wraps the minecraft BlockPos.Mutable (and BlockPos) class */
public class BiomeWrapper implements IBiomeWrapper
{
    // must be defined before AIR, otherwise a null pointer will be thrown
    private static final Logger LOGGER = LogManager.getLogger();


    public static final ConcurrentMap<BiomeGenBase, BiomeWrapper> WRAPPER_BY_BIOME = new ConcurrentHashMap<>();

    public static final ConcurrentHashMap<String, BiomeWrapper> WRAPPER_BY_RESOURCE_LOCATION = new ConcurrentHashMap<>();

    public static final String EMPTY_BIOME_STRING = "EMPTY";
    public static final BiomeWrapper EMPTY_WRAPPER = new BiomeWrapper(null, null);

    public static final String PLAINS_RESOURCE_LOCATION_STRING = "minecraft:plains";

    /** keep track of broken biomes so we don't log every time */
    private static final HashSet<String> brokenResourceLocationStrings = new HashSet<>();

    /**
     * Only display this warning once, otherwise the log may be spammed <br>
     * This is a known issue when joining Hypixel.
     */
    private static boolean emptyStringWarningLogged = false;
    private static boolean emptyLevelSerializeFailLogged = false;



    // properties //

    public final BiomeGenBase biome;

    /** technically final, but since it requires a method call to generate it can't be marked as such */
    private String serialString;
    private final int hashCode;



    //==============//
    // constructors //
    //==============//

    static public IBiomeWrapper getBiomeWrapper(BiomeGenBase biome, ILevelWrapper levelWrapper)
    {
        if (biome == null)
        {
            return EMPTY_WRAPPER;
        }


        if (WRAPPER_BY_BIOME.containsKey(biome))
        {
            return WRAPPER_BY_BIOME.get(biome);
        }
        else
        {
            BiomeWrapper newWrapper = new BiomeWrapper(biome, levelWrapper);
            WRAPPER_BY_BIOME.put(biome, newWrapper);
            return newWrapper;
        }
    }
    private BiomeWrapper(BiomeGenBase biome, ILevelWrapper levelWrapper)
    {
        this.biome = biome;
        this.serialString = this.serialize(levelWrapper);
        this.hashCode = Objects.hash(this.serialString);

        //LOGGER.trace("Created BiomeWrapper ["+this.serialString+"] for ["+biome+"]");
    }

    /** should only be used to create {@link BiomeWrapper#EMPTY_WRAPPER} */
    private BiomeWrapper()
    {
        this.biome = null;
        this.serialString = EMPTY_BIOME_STRING;
        this.hashCode = Objects.hash(this.serialString);
    }



    //=========//
    // methods //
    //=========//

    @Override
    public String getName()
    {
        if (this == EMPTY_WRAPPER)
        {
            return EMPTY_BIOME_STRING;
        }

        return biome.biomeName;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        else if (obj == null || this.getClass() != obj.getClass())
        {
            return false;
        }

        BiomeWrapper that = (BiomeWrapper) obj;
        // the serialized value is used so we can test the contents instead of the references
        return Objects.equals(this.getSerialString(), that.getSerialString());
    }

    @Override
    public int hashCode() { return this.hashCode; }

    @Override
    public String getSerialString() { return this.serialString; }

    @Override
    public Object getWrappedMcObject() { return this.biome; }

    @Override
    public String toString() { return this.getSerialString(); }



    //=======================//
    // serialization methods //
    //=======================//

    public String serialize(ILevelWrapper levelWrapper)
    {
        if (this.biome == null)
        {
            return EMPTY_BIOME_STRING;
        }



        // we can't generate a serial string if the level is null
        if (levelWrapper == null)
        {
            if (!emptyLevelSerializeFailLogged)
            {
                emptyLevelSerializeFailLogged = true;
                LOGGER.warn("Unable to serialize biome: [" + this.biome + "] because the passed in level wrapper is null. Future errors of this type won't be logged.");
            }

            return EMPTY_BIOME_STRING;
        }



        // generate the serial string //

        this.serialString = "biome:" + this.biome.biomeName;

        return this.serialString;
    }

    // TODO would it be worth while to cache these objects in a ConcurrentHashMap<string, IBiomeWrapper>?
    public static IBiomeWrapper deserialize(String resourceLocationString, ILevelWrapper levelWrapper) throws IOException
    {
        // we need the final string for the concurrent hash map later
        final String finalResourceStateString = resourceLocationString;

        if (resourceLocationString.equals(EMPTY_BIOME_STRING))
        {
            if (!emptyStringWarningLogged)
            {
                emptyStringWarningLogged = true;
                LOGGER.warn("[" + EMPTY_BIOME_STRING + "] biome string deserialized. This may mean the level was null when a save was attempted, a file saving error, or a biome saving error. Future errors will not be logged.");
            }
            return EMPTY_WRAPPER;
        }
        else if (resourceLocationString.trim().isEmpty() || resourceLocationString.equals(""))
        {
            LOGGER.warn("Null biome string deserialized.");
            return EMPTY_WRAPPER;
        }

        if (WRAPPER_BY_RESOURCE_LOCATION.containsKey(finalResourceStateString))
        {
            return WRAPPER_BY_RESOURCE_LOCATION.get(finalResourceStateString);
        }



        // if no wrapper is found, default to the empty wrapper
        BiomeWrapper foundWrapper = EMPTY_WRAPPER;
        try
        {
            // parse the resource location
            int separatorIndex = resourceLocationString.indexOf(":");
            if (separatorIndex == -1)
            {
                throw new IOException("Unable to parse resource location string: [" + resourceLocationString + "].");
            }


            try
            {
                BiomeGenBase foundBiome = null;

                for (BiomeGenBase biome : BiomeGenBase.getBiomeGenArray())
                {
                    String id = "biome:" + biome.biomeName;
                    if (id.equals(resourceLocationString))
                    {
                        foundBiome = biome;
                        break;
                    }
                }

                if (foundBiome == null)
                {
                    if (!brokenResourceLocationStrings.contains(resourceLocationString))
                    {
                        brokenResourceLocationStrings.add(resourceLocationString);
                        LOGGER.warn("Unable to deserialize biome from string: [" + resourceLocationString + "]");
                    }
                    return EMPTY_WRAPPER;
                }


                foundWrapper = (BiomeWrapper) getBiomeWrapper(foundBiome, levelWrapper);
                return foundWrapper;
            }
            catch (Exception e)
            {
                throw new IOException("Failed to deserialize the string [" + finalResourceStateString + "] into a BiomeWrapper: " + e.getMessage(), e);
            }
        }
        finally
        {
            WRAPPER_BY_RESOURCE_LOCATION.putIfAbsent(finalResourceStateString, foundWrapper);
        }
    }

}
