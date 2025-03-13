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

import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataRepo;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;

/**
 * Fired whenever Distant Horizons has been notified
 * that a Minecraft chunk has been modified. <br>
 * By the time this event has been fired, the chunk modification should have propagated
 * to DH's full data source, but may not have been updated in the render data source.
 *
 * @author James Seibel
 * @version 2023-6-23
 * @see IDhApiTerrainDataRepo
 * @since API 1.0.0
 */
public abstract class DhApiChunkModifiedEvent implements IDhApiEvent<DhApiChunkModifiedEvent.EventParam>
{
	/** Fired after Distant Horizons saves LOD data for the server. */
	public abstract void onChunkModified(DhApiEventParam<EventParam> input);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<EventParam> input) { this.onChunkModified(input); }
	
	
	//==================//
	// parameter object //
	//==================//
	
	public static class EventParam implements IDhApiEventParam
	{
		/** The saved level. */
		public final IDhApiLevelWrapper levelWrapper;
		
		/** the modified chunk's X pos in chunk coordinates */
		public final int chunkX;
		/** the modified chunk's Z pos in chunk coordinates */
		public final int chunkZ;
		
		
		public EventParam(IDhApiLevelWrapper newLevelWrapper, int chunkX, int chunkZ)
		{
			this.levelWrapper = newLevelWrapper;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
		}
		
		
		@Override
		public EventParam copy()
		{
			return new EventParam(
					this.levelWrapper,
					this.chunkX, this.chunkZ
			);
		}
	}
	
}