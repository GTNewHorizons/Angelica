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

package com.seibel.distanthorizons.core.world;

import com.seibel.distanthorizons.core.level.IDhServerLevel;
import com.seibel.distanthorizons.core.multiplayer.server.ServerPlayerStateManager;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;

/** Used both for dedicated server and singleplayer worlds */
public interface IDhServerWorld extends IDhWorld
{
	ServerPlayerStateManager getServerPlayerStateManager();
	void addPlayer(IServerPlayerWrapper serverPlayer);
	void removePlayer(IServerPlayerWrapper serverPlayer);
	void changePlayerLevel(IServerPlayerWrapper player, IServerLevelWrapper originLevel, IServerLevelWrapper destinationLevel);
	void serverTick();
	
	default IDhServerLevel getOrLoadServerLevel(ILevelWrapper levelWrapper) { return (IDhServerLevel) this.getOrLoadLevel(levelWrapper); }
	
}
