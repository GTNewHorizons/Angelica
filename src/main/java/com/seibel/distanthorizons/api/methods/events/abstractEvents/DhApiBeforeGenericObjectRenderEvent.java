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

import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiCancelableEvent;
import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

/**
 * Called before Distant Horizons starts rendering a generic object. <br>
 * Canceling this event will prevent the triggering {@link IDhApiRenderableBoxGroup} from rendering this frame.
 *
 * @author James Seibel
 * @version 2024-7-11
 * @since API 3.0.0
 */
public abstract class DhApiBeforeGenericObjectRenderEvent implements IDhApiCancelableEvent<DhApiBeforeGenericObjectRenderEvent.EventParam>
{
	/** Fired before Distant Horizons renders a generic object. */
	public abstract void beforeRender(DhApiCancelableEventParam<EventParam> event);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiCancelableEventParam<EventParam> input) { this.beforeRender(input); }
	
	
	//==================//
	// parameter object //
	//==================//
	
	public static class EventParam extends DhApiRenderParam implements IDhApiEventParam
	{
		public final long boxGroupId;
		public final String resourceLocationNamespace;
		public final String resourceLocationPath;
		
		
		public EventParam(
				DhApiRenderParam renderParam,
				IDhApiRenderableBoxGroup boxGroup
			) 
		{
			super(renderParam); 
			
			this.boxGroupId = boxGroup.getId();
			this.resourceLocationNamespace = boxGroup.getResourceLocationNamespace();
			this.resourceLocationPath = boxGroup.getResourceLocationPath();
		}
		public EventParam(
				DhApiRenderParam renderParam,
				long boxGroupId, String resourceLocationNamespace, String resourceLocationPath
			)
		{
			super(renderParam);
			
			this.boxGroupId = boxGroupId;
			this.resourceLocationNamespace = resourceLocationNamespace;
			this.resourceLocationPath = resourceLocationPath;
		}
		
		
		
		@Override
		public EventParam copy()
		{
			return new EventParam(
				this, 
				this.boxGroupId, this.resourceLocationNamespace, this.resourceLocationPath
			);
		}
	}
	
}