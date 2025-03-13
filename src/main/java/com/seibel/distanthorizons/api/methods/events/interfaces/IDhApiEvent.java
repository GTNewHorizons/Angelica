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

package com.seibel.distanthorizons.api.methods.events.interfaces;

import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

/**
 * The interface used by all DH Api events.
 *
 * @param <T> This is the datatype that will be passed into the event handler's method.
 * @author James Seibel
 * @version 2023-6-23
 * @since API 1.0.0
 */
public interface IDhApiEvent<T> extends IBindable
{
	//==========//
	// external //
	//==========//
	
	/**
	 * Returns true if the event should be automatically unbound
	 * after firing. <br>
	 * Can be useful for one time setup events or waiting for a specific game state. <br> <Br>
	 *
	 * Defaults to False
	 * IE: The event will not be removed after firing and will continue firing until removed.
	 */
	default boolean removeAfterFiring() { return false; }
	
	
	//==========//
	// internal //
	//==========//
	
	/**
	 * Called internally by Distant Horizons when the event happens.
	 * This method shouldn't directly be overridden and
	 * should call a more specific method instead.
	 *
	 * @param input the parameter object passed in from the event source. Can be null.
	 */
	void fireEvent(DhApiEventParam<T> input);
	
}
