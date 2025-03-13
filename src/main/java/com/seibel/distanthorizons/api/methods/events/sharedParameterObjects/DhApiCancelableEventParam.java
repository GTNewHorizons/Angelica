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

package com.seibel.distanthorizons.api.methods.events.sharedParameterObjects;

/** 
 * Extension of {@link DhApiEventParam} that allows the event to be canceled. 
 * 
 * @since API 1.0.0
 */
public class DhApiCancelableEventParam<T> extends DhApiEventParam<T>
{
	public DhApiCancelableEventParam(T value) { super(value); }
	
	private boolean eventCanceled = false;
	/** Prevents the DH event from completing after all bound event handlers have been fired. */
	public void cancelEvent() { this.eventCanceled = true; }
	/** @return if this DH event has been canceled, either by this event handler or a previous one. */
	public boolean isEventCanceled() { return this.eventCanceled; }
	
}
