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

/**
 * Called whenever Distant Horizons (re)creates 
 * the color and depth textures it renders to. <br>
 * 
 * @author James Seibel
 * @version 2024-3-2
 * @since API 2.0.0
 */
public abstract class DhApiColorDepthTextureCreatedEvent implements IDhApiEvent<DhApiColorDepthTextureCreatedEvent.EventParam>
{
	/** Fired before Distant Horizons creates. */
	public abstract void onResize(DhApiEventParam<EventParam> event);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<EventParam> event) { this.onResize(event); }
	
	
	//==================//
	// parameter object //
	//==================//
	
	public static class EventParam implements IDhApiEventParam
	{
		/** Measured in pixels */
		public final int previousWidth;
		/** Measured in pixels */
		public final int previousHeight;
		
		/** Measured in pixels */
		public final int newWidth;
		/** Measured in pixels */
		public final int newHeight;
		
		
		public EventParam(
				int previousWidth, int previousHeight,
				int newWidth, int newHeight)
		{
			this.previousWidth = previousWidth;
			this.previousHeight = previousHeight;
			
			this.newWidth = newWidth;
			this.newHeight = newHeight;
			
		}
		
		
		@Override
		public EventParam copy()
		{
			return new EventParam(
					this.previousWidth, this.previousHeight,
					this.newWidth, this.newHeight
			);
		}
	}
	
}