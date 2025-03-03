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
 * Fired before DH runs its apply shader.
 * The apply shader is a shader that copies over everything DH has rendered
 * for this pass into MC's framebuffers so it can be rendered to the screen.
 * Canceling this event prevents the apply shader from running.
 * 
 * @author James Seibel
 * @version 2024-1-31
 * @since API 2.0.0
 */
public abstract class DhApiBeforeApplyShaderRenderEvent implements IDhApiCancelableEvent<DhApiRenderParam>
{
	/** Fired before the apply shader is run. */
	public abstract void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiCancelableEventParam<DhApiRenderParam> event) { this.beforeRender(event); }
	
}