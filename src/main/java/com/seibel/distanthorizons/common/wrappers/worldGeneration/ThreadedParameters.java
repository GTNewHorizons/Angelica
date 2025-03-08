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

import com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment.PerfCalculator;

import net.minecraft.world.WorldServer;

public final class ThreadedParameters
{
    private static final ThreadLocal<ThreadedParameters> LOCAL_PARAM = new ThreadLocal<>();

    final WorldServer level;
    boolean isValid = true;
    public final PerfCalculator perf = new PerfCalculator();

    private static GlobalParameters previousGlobalParameters = null;



    public static ThreadedParameters getOrMake(GlobalParameters param)
    {
        ThreadedParameters tParam = LOCAL_PARAM.get();
        if (tParam != null && tParam.isValid && tParam.level == param.level)
        {
            return tParam;
        }

        tParam = new ThreadedParameters(param);
        LOCAL_PARAM.set(tParam);
        return tParam;
    }

    private ThreadedParameters(GlobalParameters param)
    {
        previousGlobalParameters = param;

        this.level = param.level;
    }



    public void markAsInvalid() { isValid = false; }

}
