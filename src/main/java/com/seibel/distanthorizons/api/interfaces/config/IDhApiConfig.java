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

import com.seibel.distanthorizons.api.interfaces.config.both.IDhApiWorldGenerationConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.*;

/**
 * This interfaces holds all config groups
 * the API has access to for easy access.
 *
 * @author James Seibel
 * @version 2023-6-14
 * @since API 1.0.0
 */
public interface IDhApiConfig
{
	IDhApiGraphicsConfig graphics();
	IDhApiWorldGenerationConfig worldGenerator();
	IDhApiMultiplayerConfig multiplayer();
	IDhApiMultiThreadingConfig multiThreading();
	// note: DON'T add the Auto Updater to this API. We only want the user's to have the ability to control when things are downloaded to their machines.
	//IDhApiLoggingConfig logging(); // TODO implement
	IDhApiDebuggingConfig debugging();
	
}
