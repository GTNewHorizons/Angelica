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

public class ConfigUIButton extends AbstractConfigType<Runnable, ConfigUIButton>
{
	public ConfigUIButton(Runnable runnable)
	{
		super(EConfigEntryAppearance.ONLY_IN_GUI, runnable);
	}
	
	/** Runs the action of the button. NOTE: Will run on the main thread (so can halt the main process if not offloaded to a different thread) */
	public void runAction() { this.value.run(); }
	
	public static class Builder extends AbstractConfigType.Builder<Runnable, Builder>
	{
		/** Appearance shouldn't be changed */
		@Override
		public Builder setAppearance(EConfigEntryAppearance newAppearance)
		{
			return this;
		}
		
		public ConfigUIButton build()
		{
			return new ConfigUIButton(this.tmpValue);
		}
		
	}
	
}
