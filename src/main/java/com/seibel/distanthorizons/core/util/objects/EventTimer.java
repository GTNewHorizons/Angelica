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

import java.time.Duration;
import java.util.ArrayList;

public class EventTimer
{
	public static class Event
	{
		public long timeNs = -1;
		public String name;
		Event(String name)
		{
			this.name = name;
		}
		
	}
	
	long lastEventNs = -1;
	
	public ArrayList<Event> events = new ArrayList<>();
	
	public EventTimer(String firstEventName)
	{
		lastEventNs = System.nanoTime();
		events.add(new Event(firstEventName));
	}
	
	public void nextEvent(String name)
	{
		long timeNs = System.nanoTime();
		if (lastEventNs != -1 && !events.isEmpty() && events.get(events.size() - 1).timeNs == -1)
		{
			events.get(events.size() - 1).timeNs = timeNs - lastEventNs;
		}
		lastEventNs = timeNs;
		events.add(new Event(name));
	}
	
	public void complete()
	{
		long timeNs = System.nanoTime();
		if (lastEventNs != -1 && !events.isEmpty() && events.get(events.size() - 1).timeNs == -1)
		{
			events.get(events.size() - 1).timeNs = timeNs - lastEventNs;
		}
		lastEventNs = -1;
	}
	
	public long getEventTimeNs(String name)
	{
		for (Event e : events)
		{
			if (e.name.equals(name))
			{
				return e.timeNs;
			}
		}
		return -1;
	}
	
	public long getTotalTimeNs()
	{
		long total = 0;
		for (Event e : events)
		{
			if (e.timeNs != -1)
			{
				total += e.timeNs;
			}
		}
		return total;
	}
	
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		for (Event e : events)
		{
			if (e.timeNs != -1)
			{
				sb.append(e.name).append(": ").append(Duration.ofNanos(e.timeNs)).append('\n');
			}
			else
			{
				sb.append(e.name).append(": ").append("N/A").append('\n');
			}
		}
		return sb.toString();
	}
	
}
