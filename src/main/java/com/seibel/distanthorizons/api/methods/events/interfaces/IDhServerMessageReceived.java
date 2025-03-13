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
 * @author Cailin
 * @deprecated marked as deprecated since it isn't currently implemented
 */
@Deprecated
public interface IDhServerMessageReceived<T> extends IDhApiEvent<T>
{
	/**
	 * Triggered when a plugin message is received from the server.
	 *
	 * @param channel The name of the channel this was received on.
	 * @param message The message sent from the server.
	 */
	void serverMessageReceived(String channel, byte[] message);
	
}
