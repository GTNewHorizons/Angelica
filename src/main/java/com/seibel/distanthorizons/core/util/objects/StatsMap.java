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

import com.seibel.distanthorizons.core.util.math.UnitBytes;

import java.util.TreeMap;

public class StatsMap
{
	final TreeMap<String, Long> longMap = new TreeMap<String, Long>();
	final TreeMap<String, UnitBytes> bytesMap = new TreeMap<String, UnitBytes>();
	
	/**
	 *
	 */
	@SuppressWarnings("unused")
	private static final long serialVersionUID = 1926219295516863173L;
	
	public StatsMap() { super(); }
	
	public void incStat(String key)
	{
		incStat(key, 1);
	}
	public void incStat(String key, long value)
	{
		longMap.put(key, longMap.getOrDefault(key, 0L) + value);
	}
	public void incBytesStat(String key, long bytes)
	{
		long b = bytesMap.getOrDefault(key, new UnitBytes(0)).value;
		bytesMap.put(key, new UnitBytes(b + bytes));
	}
	
	@Override
	public String toString()
	{
		return longMap.toString() + " " + bytesMap.toString();
	}
	
	
}
