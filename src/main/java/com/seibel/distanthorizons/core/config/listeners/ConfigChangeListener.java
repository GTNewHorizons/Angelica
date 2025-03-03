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

package com.seibel.distanthorizons.core.config.listeners;

import com.seibel.distanthorizons.core.config.types.ConfigEntry;

import java.io.Closeable;
import java.util.function.Consumer;

/**
 * A basic {@link IConfigListener} that will fire a {@link Consumer}
 * when the value changes from the value the config started with
 * when this object was created.
 *
 * @param <T> the config value type
 */
public class ConfigChangeListener<T> implements IConfigListener, Closeable
{
	private final ConfigEntry<T> configEntry;
	private final Consumer<T> onValueChangeFunc;
	
	private T previousValue;
	
	
	
	public ConfigChangeListener(ConfigEntry<T> configEntry, Consumer<T> onValueChangeFunc)
	{
		this.configEntry = configEntry;
		this.onValueChangeFunc = onValueChangeFunc;
		
		this.configEntry.addListener(this);
		this.previousValue = this.configEntry.get();
	}
	
	
	@Override
	public void onConfigValueSet()
	{
		T newValue = this.configEntry.get();
		if (newValue != this.previousValue)
		{
			this.previousValue = newValue;
			this.onValueChangeFunc.accept(newValue);
		}
	}
	
	@Override
	public void onUiModify() { /* do nothing, we only care about when the actual value is modified */ }
	
	
	
	/**
	 * Removes the config event listener. <br>
	 * Must be fired to prevent memory leaks.
	 */
	@Override
	public void close() { this.configEntry.removeListener(this); }
	
}
