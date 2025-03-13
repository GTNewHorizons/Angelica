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

package com.seibel.distanthorizons.api.methods.events;

import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;

/**
 * Handles adding/removing event handlers.
 *
 * @author James Seibel
 * @version 2022-9-16
 * @since API 1.0.0
 */
public class DhApiEventRegister
{
	/**
	 * Registers the given event handler. <Br>
	 * Only one eventHandler of a specific class can be registered at a time.
	 * If multiple of the same eventHandler are added DhApiResult will return
	 * the name of the already added handler and success = false.
	 */
	public static DhApiResult<Void> on(Class<? extends IDhApiEvent> eventInterface, IDhApiEvent eventHandlerImplementation)
	{
		try
		{
			ApiEventInjector.INSTANCE.bind(eventInterface, eventHandlerImplementation);
			return DhApiResult.createSuccess();
		}
		catch (IllegalStateException e)
		{
			return DhApiResult.createFail(e.getMessage());
		}
	}
	
	/**
	 * Unregisters the given event handler for this event if one has been registered. <br>
	 * If no eventHandler of the given class has been registered the result will return
	 * success = false.
	 */
	public static DhApiResult<Void> off(Class<? extends IDhApiEvent> eventInterface, Class<IDhApiEvent> eventHandlerClass)
	{
		if (ApiEventInjector.INSTANCE.unbind(eventInterface, eventHandlerClass))
		{
			return DhApiResult.createSuccess();
		}
		else
		{
			return DhApiResult.createFail("No event handler [" + eventHandlerClass.getSimpleName() + "] was bound for the event [" + eventInterface.getSimpleName() + "].");
		}
	}
	
}
