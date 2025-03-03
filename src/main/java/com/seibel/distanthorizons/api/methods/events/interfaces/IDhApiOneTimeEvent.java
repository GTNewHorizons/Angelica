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

/**
 * If a {@link IDhApiEvent} implements this interface then the event will only ever be fired once. <Br>
 * An example of this would be initial setup methods, DH won't run its initial setup more than once. <br><br>
 *
 * If a handler is bound to a one time event after the event has been fired, the handler will immediately fire.
 *
 * @since API 1.0.0
 */
public interface IDhApiOneTimeEvent<T> extends IDhApiEvent<T>
{
	
}
