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

package com.seibel.distanthorizons.common.wrappers.worldGeneration;

import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.level.IDhServerLevel;
import net.minecraft.world.WorldServer;

public final class GlobalParameters
{
    public final IDhServerLevel lodLevel;
    public final WorldServer level;
    public final long worldSeed;

    public GlobalParameters(IDhServerLevel lodLevel)
    {
        this.lodLevel = lodLevel;

        this.level = ((ServerLevelWrapper) lodLevel.getServerLevelWrapper()).getWrappedMcObject();

        this.worldSeed = level.getSeed();
    }

}
