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

package com.seibel.distanthorizons.common.wrappers;

import java.util.function.Supplier;

public class DependencySetupDoneCheck
{
	// TODO move to DependencySetup
	public static boolean isDone = false;
	/** 
	 * This is used so we can override some MC logic when running
	 * in DH's world generator.
	 * Specifically so we can redirect threads to run on DH threads instead
	 * of MC threads.
	 */
	public static Supplier<Boolean> getIsCurrentThreadDistantGeneratorThread = (() -> { return false; });
	
}
