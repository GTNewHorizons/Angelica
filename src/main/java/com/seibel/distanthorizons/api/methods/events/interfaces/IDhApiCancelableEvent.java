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

package com.seibel.distanthorizons.api.methods.events.interfaces;

import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;

/**
 * If a {@link IDhApiEvent} implements this interface than the event
 * can be canceled via the {@link DhApiCancelableEventParam#cancelEvent()} method.
 *
 * @author James Seibel
 * @version 2023-6-23
 * @since API 1.0.0
 */
public interface IDhApiCancelableEvent<T> extends IDhApiEvent<T>
{
	
	void fireEvent(DhApiCancelableEventParam<T> input);
	
	/**
	 * <strong> Shouldn't be called. </strong> <br><br>
	 *
	 * The {@link IDhApiCancelableEvent#fireEvent(DhApiCancelableEventParam)} method should be used instead.
	 * This override method is present to prevent API users from having to implement it themselves.
	 *
	 * @deprecated marked as deprecated to warn that this method shouldn't be used. <br>
	 * <strong>DH Internal Note:</strong> Is there a better way to format the {@link IDhApiEvent} classes so we don't need this method?
	 * It would be better to completely hide this method so it isn't possible to accidentally call.
	 */
	@Deprecated
	@Override
	default void fireEvent(DhApiEventParam<T> input)
	{
		if (!input.getClass().isAssignableFrom(DhApiCancelableEventParam.class))
		{
			throw new IllegalArgumentException("Programmer error. [" + IDhApiCancelableEvent.class.getSimpleName() + "] was given a [" + DhApiEventParam.class.getSimpleName() + "] when it should only be given a [" + DhApiCancelableEventParam.class.getSimpleName() + "].");
		}
		
		this.fireEvent((DhApiCancelableEventParam<T>) input);
	}
	
}
