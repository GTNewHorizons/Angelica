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

import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiCancelableEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

/**
 * Called before Distant Horizons starts rendering a frame. <br>
 * Canceling the event will prevent DH from rendering that frame. <br> <br>
 * 
 * This is called before DH starts modifying the GL state.
 * If you want to inject into DH's rendering pass, use {@link DhApiBeforeRenderPassEvent} instead.
 *
 * @author James Seibel
 * @version 2023-6-23
 * @since API 1.0.0
 * 
 * @see DhApiBeforeRenderPassEvent
 */
public abstract class DhApiBeforeRenderEvent implements IDhApiCancelableEvent<DhApiRenderParam>
{
	/** Fired before Distant Horizons renders LODs. */
	public abstract void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiCancelableEventParam<DhApiRenderParam> input) { this.beforeRender(input); }
	
}