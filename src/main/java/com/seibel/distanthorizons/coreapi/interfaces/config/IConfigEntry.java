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

package com.seibel.distanthorizons.coreapi.interfaces.config;


import java.util.function.Consumer;

/**
 * Use for making the config variables
 *
 * @author coolGi
 * @version 2022-5-26
 */
public interface IConfigEntry<T>
{
	
	/** Gets the default value of the option */
	T getDefaultValue();
	
	void setApiValue(T newApiValue);
	T getApiValue();
	
	/** Returns true if this config can be set via the API. */
	boolean getAllowApiOverride();
	
	void set(T newValue);
	T get();
	T getTrueValue();
	
	/** Sets the value without saving */
	void setWithoutSaving(T newValue);
	
	/** Gets the min value */
	T getMin();
	/** Sets the min value */
	void setMin(T newMin);
	/** Gets the max value */
	T getMax();
	/** Sets the max value */
	void setMax(T newMax);
	/** Sets the min and max in 1 setter */
	void setMinMax(T newMin, T newMax);
	
	/** Gets the comment */
	String getComment();
	/** Sets the comment */
	void setComment(String newComment);
	
	/**
	 * Checks if the option is valid
	 *
	 * 0 == valid
	 * 2 == invalid
	 * 1 == number too high
	 * -1 == number too low
	 */
	byte isValid(); // TODO replace with an enum
	/** Checks if a value is valid */
	byte isValid(T value);
	
	/** Is the value of this equal to another */
	boolean equals(IConfigEntry<?> obj);
	
	void addValueChangeListener(Consumer<T> onValueChangeFunc);
	
}
