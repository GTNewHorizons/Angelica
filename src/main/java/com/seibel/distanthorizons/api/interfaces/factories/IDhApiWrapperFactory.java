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

package com.seibel.distanthorizons.api.interfaces.factories;

import com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.DhApi;

import java.io.IOException;

/**
 * This handles creating abstract wrapper objects.
 *
 * @author James Seibel
 * @version 2023-12-16
 * @since API 2.0.0
 */
public interface IDhApiWrapperFactory
{
	/**
	 * Constructs a {@link IDhApiBiomeWrapper} for use by other DhApi methods.
	 * 
	 * @param objectArray Expects the following Minecraft objects (in order) for each MC version: <br>
	 * <b>1.16</b> and <b>1.17</b><br>
	 * - [net.minecraft.world.level.biome.Biome] <br>
	 * <b>1.18</b> and <b>newer</b> <br>
	 * - {@literal [net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>] }<br>
	 * 
	 * @param levelWrapper Expects a {@link IDhApiLevelWrapper} returned by one of DH's {@link DhApi.Delayed#worldProxy} methods. <br>
	 *                      A custom implementation of {@link IDhApiLevelWrapper} will not be accepted.
	 * 
	 * @throws ClassCastException if any of the given parameters is of the wrong type. 
	 * If thrown the error message will contain the list of expected object types in order. 
	 * 
	 * @since API 2.0.0
	 */
	IDhApiBiomeWrapper getBiomeWrapper(Object[] objectArray, IDhApiLevelWrapper levelWrapper) throws ClassCastException;
	
	/**
	 * Constructs a {@link IDhApiBlockStateWrapper} for use by other DhApi methods.
	 *
	 * @param objectArray Expects the following Minecraft objects (in order) for each MC version: <br>
	 * <b>1.16</b> and <b>newer</b> <br>
	 * - [net.minecraft.world.level.block.state.BlockState]<br>
	 *
	 * @param levelWrapper Expects a {@link IDhApiBlockStateWrapper} returned by one of DH's {@link DhApi.Delayed#worldProxy} methods. <br>
	 *                      A custom implementation of {@link IDhApiBlockStateWrapper} will not be accepted.
	 *
	 * @throws ClassCastException if any of the given parameters is of the wrong type. 
	 * If thrown the error message will contain the list of expected object types in order. 
	 * 
	 * @since API 2.0.0
	 */
	IDhApiBlockStateWrapper getBlockStateWrapper(Object[] objectArray, IDhApiLevelWrapper levelWrapper) throws ClassCastException;
	
	/**
	 * Returns the {@link IDhApiBlockStateWrapper} representing air.
	 * @since API 2.0.0
	 */
	IDhApiBlockStateWrapper getAirBlockStateWrapper();
	
	
	
	/**
	 * Constructs a {@link IDhApiBiomeWrapper} for use by other DhApi methods.
	 *
	 * @param resourceLocationString example: "minecraft:plains"
	 *
	 * @param levelWrapper Expects a {@link IDhApiLevelWrapper} returned by one of DH's {@link DhApi.Delayed#worldProxy} methods. <br>
	 *                      A custom implementation of {@link IDhApiLevelWrapper} will not be accepted.
	 *
	 * @throws IOException if the resourceLocationString wasn't able to be parsed or converted into a valid {@link IDhApiBiomeWrapper}
	 * @throws ClassCastException if the wrong levelWrapper type was given
	 *
	 * @since API 3.0.0
	 */
	IDhApiBiomeWrapper getBiomeWrapper(String resourceLocationString, IDhApiLevelWrapper levelWrapper) throws IOException, ClassCastException;
	
	/**
	 * Constructs a {@link IDhApiBlockStateWrapper} for use by other DhApi methods.
	 * This returns the default blockstate for the given resource location.
	 *
	 * @param resourceLocationString examples: "minecraft:bedrock", "minecraft:stone", "minecraft:grass_block"
	 * @param levelWrapper Expects a {@link IDhApiBlockStateWrapper} returned by one of DH's {@link DhApi.Delayed#worldProxy} methods. <br>
	 *                      A custom implementation of {@link IDhApiBlockStateWrapper} will not be accepted.
	 *
	 * @throws IOException if the resourceLocationString wasn't able to be parsed or converted into a valid {@link IDhApiBlockStateWrapper}
	 * @throws ClassCastException if the wrong levelWrapper type was given
	 *
	 * @since API 3.0.0
	 */
	IDhApiBlockStateWrapper getDefaultBlockStateWrapper(String resourceLocationString, IDhApiLevelWrapper levelWrapper) throws IOException, ClassCastException;
	
}
