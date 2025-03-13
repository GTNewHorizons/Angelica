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

package com.seibel.distanthorizons.core.util.objects;

import java.util.Objects;

/** A simple way to hold 2 objects together */
public final class Pair<T, U>
{
	public final T first;
	public final U second;
	
	public Pair(T first, U second)
	{
		this.second = second;
		this.first = first;
	}
	
	@Override
	public String toString() { return "(" + this.first + ", " + this.second + ")"; }
	
	@Override
	public int hashCode() { return Objects.hash(this.first, this.second); }
	
}
