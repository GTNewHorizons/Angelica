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
 * Distant Horizons' generic rendering configuration. <br><br>
 *
 * @author James Seibel
 * @version 2024-7-11
 * @since API 3.0.0
 */
public interface IDhApiGenericRenderingConfig extends IDhApiConfigGroup
{
	/** 
	 * If enabled DH will render generic objects into its terrain pass. <br>
	 * This includes: clouds, beacons, and API added objects.
	 */
	IDhApiConfigValue<Boolean> renderingEnabled();
	
	/** If enabled DH will render beacon beams. */
	IDhApiConfigValue<Boolean> beaconRenderingEnabled();
	
	/** If enabled DH will render clouds. */
	IDhApiConfigValue<Boolean> cloudRenderingEnabled();
	
}
