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

package com.seibel.distanthorizons.api.interfaces.config;

import java.util.function.Consumer;

/**
 * An interface for Distant Horizon's Config.
 *
 * @param <T> The data type of this config.
 * @author James Seibel
 * @version 2023-12-7
 * @since API 1.0.0
 */
public interface IDhApiConfigValue<T>
{
	
	/**
	 * Returns the active value for this config. <br>
	 * Returns the True value if either the config cannot be overridden by
	 * the API or if it hasn't been set by the API.
	 */
	T getValue();
	/**
	 * Returns the value held by this config. <br>
	 * This is the value stored in the config file.
	 */
	T getTrueValue();
	/**
	 * Returns the value of the config if it was set by the API.
	 * Returns null if the config hasn't been set by the API.
	 * 
	 * @since API 2.0.0
	 */
	T getApiValue();
	
	/**
	 * Sets the config's value. <br>
	 * If the newValue is set to null then the config
	 * will revert to using the True Value 
	 * (IE the value visible in the config menu).<br>
	 * If the config cannot be set via the API this method will return false. <br><br>
	 * 
	 * @return true if the value was set, false otherwise.
	 */
	boolean setValue(T newValue);
	
	/**
	 * Un-sets the config's API value. <br>
	 * After this method is called this config will
	 * use the value set in the config menu.
	 * 
	 * @return true if the value was set, false otherwise.
	 * @since API 2.0.0
	 */
	boolean clearValue();
	
	/** @return true if this config can be set via the API, false otherwise. */
	boolean getCanBeOverrodeByApi();
	
	/** Returns the default value for this config. */
	T getDefaultValue();
	/** Returns the max value for this config, null if there is no max. */
	T getMaxValue();
	/** Returns the min value for this config, null if there is no min. */
	T getMinValue();
	
	/** Adds a {@link Consumer} that will be called whenever the config changes to a new value. */
	void addChangeListener(Consumer<T> onValueChangeFunc);
	//void removeListener(Consumer<T> onValueChangeFunc); // not currently implemented
	
}
