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

package com.seibel.distanthorizons.core.wrapperInterfaces.minecraft;

import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

/**
 * @author James Seibel
 * @version 11-20-2021
 */
public interface IProfilerWrapper extends IBindable
{
	// Note to self:
	// if "unspecified" shows up in the pie chart, it is
	// possibly because the amount of time between sections
	// is too small for the profiler to measures
	void push(String newSection);
	
	void popPush(String newSection);
	
	void pop();
	
}
