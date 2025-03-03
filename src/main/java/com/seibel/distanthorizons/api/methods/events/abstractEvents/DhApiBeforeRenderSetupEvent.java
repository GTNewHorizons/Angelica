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

import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

/**
 * Called before Distant Horizons has started setting up OpenGL objects for rendering. <br>
 * If you want to modify already bound DH OpenGL objects try using {@link DhApiBeforeRenderPassEvent}.
 * 
 * @author James Seibel
 * @version 2024-1-31
 * @since API 2.0.0
 * 
 * @see DhApiBeforeRenderPassEvent
 */
public abstract class DhApiBeforeRenderSetupEvent implements IDhApiEvent<DhApiRenderParam>
{
	/** Fired before Distant Horizons has started setting up OpenGL objects for rendering. */
	public abstract void beforeSetup(DhApiEventParam<DhApiRenderParam> input);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<DhApiRenderParam> input) { this.beforeSetup(input); }
	
	
}