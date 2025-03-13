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
 * Adds something like a ConfigEntry but without a button to change the input
 *
 * @author coolGi
 */
public class ConfigUIComment extends AbstractConfigType<String, ConfigUIComment>
{
	public ConfigUIComment()
	{
		super(EConfigEntryAppearance.ONLY_IN_GUI, "");
	}
	
	/** Appearance shouldn't be changed */
	@Override
	public void setAppearance(EConfigEntryAppearance newAppearance) { }
	
	/** Pointless to set the value */
	@Override
	public void set(String newValue) { }
	
	public static class Builder extends AbstractConfigType.Builder<String, Builder>
	{
		/** Appearance shouldn't be changed */
		@Override
		public Builder setAppearance(EConfigEntryAppearance newAppearance)
		{
			return this;
		}
		
		/** Pointless to set the value */
		@Override
		public Builder set(String newValue)
		{
			return this;
		}
		
		public ConfigUIComment build()
		{
			return new ConfigUIComment();
		}
		
	}
	
}
