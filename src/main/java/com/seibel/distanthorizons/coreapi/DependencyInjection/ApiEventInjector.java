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

package com.seibel.distanthorizons.coreapi.DependencyInjection;

import com.seibel.distanthorizons.api.interfaces.events.IDhApiEventInjector;
import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiCancelableEvent;
import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiOneTimeEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;

/** This class takes care of dependency injection for API events. */
public class ApiEventInjector extends DependencyInjector<IDhApiEvent> implements IDhApiEventInjector // Note to self: Don't try adding a generic type to IDhApiEvent, the constructor won't accept it
{
	public static final ApiEventInjector INSTANCE = new ApiEventInjector();
	
	private static final Logger LOGGER = LogManager.getLogger(ApiEventInjector.class.getSimpleName());
	
	private final HashMap<Class<? extends IDhApiEvent>, Object> firedOneTimeEventParamsByEventInterface = new HashMap<>();
	
	
	
	private ApiEventInjector() { super(IDhApiEvent.class, true); }
	
	
	
	@SuppressWarnings("unchecked")
	@Override
	public void bind(Class<? extends IDhApiEvent> abstractEvent, IDhApiEvent eventImplementation) throws IllegalStateException, IllegalArgumentException
	{
		// is this a one time event?
		if (IDhApiOneTimeEvent.class.isAssignableFrom(abstractEvent))
		{
			// has this one time event been fired yet?
			if (this.firedOneTimeEventParamsByEventInterface.containsKey(abstractEvent))
			{
				// the one time event has happened, fire the handler
				
				// this has to be an unsafe cast since the hash map can't hold the generic objects
				Object parameter = this.firedOneTimeEventParamsByEventInterface.get(abstractEvent);
				// one time events probably aren't cancelable, but just in case
				DhApiEventParam<?> eventParam = createEventParamWrapper(eventImplementation, parameter);
				eventImplementation.fireEvent(eventParam);
			}
		}
		
		// bind the event handler
		super.bind(abstractEvent, eventImplementation);
	}
	
	@Override
	public boolean unbind(Class<? extends IDhApiEvent> abstractEvent, Class<? extends IDhApiEvent> eventClassToRemove) throws IllegalArgumentException
	{
		// make sure the given dependency implements the necessary interfaces
		boolean implementsInterface = this.checkIfClassImplements(eventClassToRemove, abstractEvent) ||
				this.checkIfClassExtends(eventClassToRemove, abstractEvent);
		boolean implementsBindable = this.checkIfClassImplements(eventClassToRemove, this.bindableInterface);
		
		// display any errors
		if (!implementsInterface)
		{
			throw new IllegalArgumentException("The event handler [" + eventClassToRemove.getSimpleName() + "] doesn't implement or extend: [" + abstractEvent.getSimpleName() + "].");
		}
		if (!implementsBindable)
		{
			throw new IllegalArgumentException("The event handler [" + eventClassToRemove.getSimpleName() + "] doesn't implement the interface: [" + IBindable.class.getSimpleName() + "].");
		}
		
		
		// actually remove the dependency
		if (this.dependencies.containsKey(abstractEvent))
		{
			ArrayList<IDhApiEvent> dependencyList = this.dependencies.get(abstractEvent);
			int indexToRemove = -1;
			for (int i = 0; i < dependencyList.size(); i++)
			{
				IBindable dependency = dependencyList.get(i);
				if (dependency.getClass().equals(eventClassToRemove))
				{
					indexToRemove = i;
					break;
				}
			}
			
			if (indexToRemove != -1)
			{
				return dependencyList.remove(indexToRemove) != null;
			}
		}
		
		// no item was removed
		return false;
	}
	
	@Override
	public <T, U extends IDhApiEvent<T>> boolean fireAllEvents(Class<U> abstractEventClass, T eventInput)
	{
		// if this is a one time event, record that it was called
		if (IDhApiOneTimeEvent.class.isAssignableFrom(abstractEventClass) &&
				!this.firedOneTimeEventParamsByEventInterface.containsKey(abstractEventClass))
		{
			this.firedOneTimeEventParamsByEventInterface.put(abstractEventClass, eventInput);
		}
		
		
		// fire each bound event
		boolean cancelEvent = false;
		ArrayList<U> eventList = this.getAll(abstractEventClass);
		ArrayList<IDhApiEvent<T>> eventsToRemove = new ArrayList<>();
		
		for (IDhApiEvent<T> event : eventList)
		{
			if (event != null)
			{
				try
				{
					// fire each event and record if any of them
					// request to cancel the event.
					
					
					// attempt to clone the event input if possible
					// this is done to reduce the likely hood that one event listener 
					// will make change the event parameter for other listeners 
					T input = eventInput;
					if (eventInput instanceof IDhApiEventParam)
					{
						try
						{
							//noinspection unchecked
							input = (T) ((IDhApiEventParam) eventInput).copy();
						}
						catch (Exception e)
						{
							LOGGER.error("Unable to clone event parameter ["+eventInput.getClass().getSimpleName()+"], error: ["+e.getMessage()+"].", e);
						}
					}
					
					
					DhApiEventParam<T> eventParam = createEventParamWrapper(event, input);
					event.fireEvent(eventParam);
					
					if (eventParam instanceof DhApiCancelableEventParam)
					{
						DhApiCancelableEventParam<T> cancelableEventParam = (DhApiCancelableEventParam<T>) eventParam;
						cancelEvent |= cancelableEventParam.isEventCanceled();
					}
					
					if (event.removeAfterFiring())
					{
						eventsToRemove.add(event);
					}
				}
				catch (Exception e)
				{
					LOGGER.error("Exception thrown by event handler [" + event.getClass().getSimpleName() + "] for event type [" + abstractEventClass.getSimpleName() + "], error:" + e.getMessage(), e);
				}
			}
		}
		
		
		// remove any removeAfterFire events
		for (IDhApiEvent<T> eventToRemove : eventsToRemove)
		{
			this.unbind(abstractEventClass, eventToRemove.getClass());
		}
		
		return cancelEvent;
	}
	
	
	/** 
	 * Wraps the event parameter object in a {@link DhApiCancelableEventParam} or {@link DhApiEventParam} depending on
	 * if it should allow cancellation or not.
	 * 
	 * @param event the event instance
	 * @param parameter the event's parameter object
	 * @param <T> the event parameter type
	 * @return the event parameter wrapped in a {@link DhApiCancelableEventParam} or {@link DhApiEventParam}
	 */
	public static <T> DhApiEventParam<T> createEventParamWrapper(IDhApiEvent<T> event, T parameter)
	{
		return (event instanceof IDhApiCancelableEvent) ? new DhApiCancelableEventParam<>(parameter) : new DhApiEventParam<>(parameter);
	}
	
}
