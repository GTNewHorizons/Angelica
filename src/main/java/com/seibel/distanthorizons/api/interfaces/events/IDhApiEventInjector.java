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

package com.seibel.distanthorizons.api.interfaces.events;

import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IDependencyInjector;

/**
 * This class takes care of dependency injection for API events.
 *
 * @author James Seibel
 * @version 2022-9-13
 * @since API 1.0.0
 */
public interface IDhApiEventInjector extends IDependencyInjector<IDhApiEvent>
{
	
	/**
	 * Unlinks the given event handler, preventing the handler from being called in the future.
	 *
	 * @param dependencyInterface the base interface for the {@link IDhApiEvent}
	 * @param dependencyClassToRemove the concrete {@link IDhApiEvent} class to remove
	 * @return true if the handler was unbound, false if the handler wasn't bound.
	 * @throws IllegalArgumentException if the implementation object doesn't implement the interface
	 */
	// Note to self: Don't try adding a generic type to IDhApiEvent, the constructor won't accept it
	boolean unbind(Class<? extends IDhApiEvent> dependencyInterface, Class<? extends IDhApiEvent> dependencyClassToRemove) throws IllegalArgumentException;
	
	
	/**
	 * Fires all bound events of the given type (does nothing if no events are bound).
	 *
	 * @param abstractEvent event type
	 * @param eventParameterObject event parameter
	 * @param <T> the parameter type taken by the event handlers.
	 * @param <U> the {@link IDhApiEvent}'s class
	 * @return if any of the bound event handlers notified that this event should be canceled.
	 */
	<T, U extends IDhApiEvent<T>> boolean fireAllEvents(Class<U> abstractEvent, T eventParameterObject);
	
}
