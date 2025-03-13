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

package com.seibel.distanthorizons.api.interfaces.config.client;

import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigGroup;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;

/**
 * Distant Horizons' noise texture configuration. <br><br>
 *
 * @author James Seibel
 * @version 2022-6-14
 * @since API 1.0.0
 */
public interface IDhApiNoiseTextureConfig extends IDhApiConfigGroup
{
	/** If enabled a noise texture will be rendered on the LODs. */
	IDhApiConfigValue<Boolean> noiseEnabled();
	
	/** Defines how many steps of noise should be applied. */
	IDhApiConfigValue<Integer> noiseSteps();
	
	/** Defines how intense the noise will be. */
	IDhApiConfigValue<Double> noiseIntensity();
	
	/**
	 * Defines how far should the noise texture render before it fades away. (in blocks) <br>
	 * Set to 0 to disable noise from fading away
	 */
	IDhApiConfigValue<Integer> noiseDropoff();
	
}
