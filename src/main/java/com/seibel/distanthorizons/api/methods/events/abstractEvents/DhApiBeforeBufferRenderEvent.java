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
import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;

/**
 * Called before Distant Horizons starts rendering a buffer. <br>
 * This event cannot be cancelled, use {@link DhApiBeforeRenderEvent} if you want to cancel rendering.
 * 
 * @author James Seibel
 * @version 2023-1-31
 * @since API 2.0.0
 * 
 * @see DhApiBeforeRenderEvent
 */
public abstract class DhApiBeforeBufferRenderEvent implements IDhApiEvent<DhApiBeforeBufferRenderEvent.EventParam>
{
	/** Fired immediately before Distant Horizons starts rendering a buffer. */
	public abstract void beforeRender(DhApiEventParam<EventParam> input);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<EventParam> input) { this.beforeRender(input); }
	
	
	//==================//
	// parameter object //
	//==================//
	
	public static class EventParam extends DhApiRenderParam implements IDhApiEventParam
	{
		/** 
		 * Measured in blocks.
		 * Should be applied to the model view matrix to move the buffer into its proper place. 
		 */
		public final DhApiVec3f modelPos;
		
		
		public EventParam(DhApiRenderParam parent, DhApiVec3f modelPos)
		{
			super(parent);
			this.modelPos = modelPos;
		}
		
		
		@Override
		public EventParam copy()
		{
			return new EventParam(
					this, this.modelPos.copy()
			);
		}
	}
	
}