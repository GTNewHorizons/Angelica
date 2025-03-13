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

import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigGroup;

/**
 * Distant Horizons' debug configuration.
 *
 * @author James Seibel
 * @version 2022-9-15
 * @since API 1.0.0
 */
public interface IDhApiDebuggingConfig extends IDhApiConfigGroup
{
	/** Can be used to debug the standard fake chunk rendering. */
	IDhApiConfigValue<EDhApiDebugRendering> debugRendering();
	
	/** If enabled debug keybindings can be used. */
	IDhApiConfigValue<Boolean> debugKeybindings();
	
	/** If enabled all LODs will be rendered as wireframe. */
	IDhApiConfigValue<Boolean> renderWireframe();
	
	/** If enabled Distant Horizons will attempt to disable Minecraft's block rendering so only LODs will render. */
	IDhApiConfigValue<Boolean> lodOnlyMode();
	
	/** If enabled Distant Horizons' debug wireframe rendering will be enabled. */
	IDhApiConfigValue<Boolean> debugWireframeRendering();
	
	
}
