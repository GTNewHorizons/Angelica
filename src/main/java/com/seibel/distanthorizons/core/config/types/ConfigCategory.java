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

package com.seibel.distanthorizons.core.config.types;

import com.seibel.distanthorizons.core.config.types.enums.EConfigEntryAppearance;

/**
 * Adds a category to the config
 * See our config file for more information on how to use it
 *
 * @author coolGi
 */
public class ConfigCategory extends AbstractConfigType<Class<?>, ConfigCategory>
{
	/** This should not be set by anything other than the config system itself */
	public String destination;    // Where the category goes to
	
	private ConfigCategory(EConfigEntryAppearance appearance, Class<?> value, String destination)
	{
		super(appearance, value);
		this.destination = destination;
	}
	
	public String getDestination()
	{
		return this.destination;
	}
	
	/** Use get() instead for category */
	@Override
	@Deprecated
	public Class<?> getType()
	{
		return value;
	}
	
	public static class Builder extends AbstractConfigType.Builder<Class<?>, Builder>
	{
		private String tmpDestination = null;
		
		public Builder setDestination(String newDestination)
		{
			this.tmpDestination = newDestination;
			return this;
		}
		
		public Builder setAppearance(EConfigEntryAppearance newAppearance)
		{
			this.tmpAppearance = newAppearance;
			return this;
		}
		
		public ConfigCategory build()
		{
			return new ConfigCategory(tmpAppearance, tmpValue, tmpDestination);
		}
		
	}
	
}
