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

import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigGroup;

/**
 * Distant Horizons' threading configuration.
 *
 * @author James Seibel
 * @version 2024-12-26
 * @since API 1.0.0
 */
public interface IDhApiMultiThreadingConfig extends IDhApiConfigGroup
{
	
	/**
	 * Defines how many threads Distant Horizons
	 * uses.
	 * 
	 * @since API 4.0.0
	 */
	IDhApiConfigValue<Integer> threadCount();
	
	/**
	 * Defines how many long Distant Horizons
	 * threads will spend running vs sleeping.
	 * This is helpful when reducing the CPU
	 * load on low end CPUs.
	 * 1.0 = 100% uptime
	 * 0.5 = 50% uptime
	 * 0.1 = 10% uptime
	 *
	 * @since API 4.0.0
	 */
	IDhApiConfigValue<Double> threadRuntimeRatio();
	
}
