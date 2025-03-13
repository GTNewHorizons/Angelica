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

package com.seibel.distanthorizons.api.methods.events.abstractEvents;

import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy;

/**
 * Called before Distant Horizons starts rendering the deferred rendering pass. <br>
 * Will only happen if {@link IDhApiRenderProxy#getDeferTransparentRendering()} is true. <br>
 * Generally this is only used when shaders are enabled. <br>
 * Canceling the event will prevent DH from rendering the deferred pass that frame.
 *
 * @author James Seibel
 * @version 2024-1-22
 * @since API 2.0.0
 */
public abstract class DhApiBeforeDeferredRenderEvent extends DhApiBeforeRenderEvent
{
	
}