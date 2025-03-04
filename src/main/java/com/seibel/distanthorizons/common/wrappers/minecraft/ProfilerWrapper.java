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

package com.seibel.distanthorizons.common.wrappers.minecraft;

import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import net.minecraft.profiler.Profiler;

/**
 * @author James Seibel
 * @version 11-20-2021
 */
public class ProfilerWrapper implements IProfilerWrapper
{
    public Profiler profiler;

    public ProfilerWrapper(Profiler profiler)
    {
        this.profiler = profiler;
    }

	/** starts a new section inside the currently running section */
	@Override
	public void push(String newSection) { profiler.startSection(newSection);}

	/** ends the currently running section and starts a new one */
	@Override
	public void popPush(String newSection) { profiler.endStartSection(newSection); }

	/** ends the currently running section */
	@Override
	public void pop() {profiler.endSection(); }

}
