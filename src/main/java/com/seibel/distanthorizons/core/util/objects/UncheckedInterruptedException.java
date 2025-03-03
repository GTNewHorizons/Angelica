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

import com.seibel.distanthorizons.core.util.LodUtil;

import java.util.concurrent.CompletionException;

public class UncheckedInterruptedException extends RuntimeException
{
	public UncheckedInterruptedException(String message)
	{
		super(message);
	}
	public UncheckedInterruptedException(Throwable cause)
	{
		super(cause);
	}
	public UncheckedInterruptedException(String message, Throwable cause)
	{
		super(message, cause);
	}
	public UncheckedInterruptedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace)
	{
		super(message, cause, enableSuppression, writableStackTrace);
	}
	public UncheckedInterruptedException()
	{
		super();
	}
	
	public static void throwIfInterrupted()
	{
		if (Thread.currentThread().isInterrupted())
		{
			throw new UncheckedInterruptedException();
		}
	}
	
	public static UncheckedInterruptedException convert(InterruptedException e)
	{
		return new UncheckedInterruptedException(e);
	}
	
	public static void rethrowIfIsInterruption(Throwable t)
	{
		if (t instanceof InterruptedException)
		{
			throw convert((InterruptedException) t);
		}
		else if (t instanceof UncheckedInterruptedException)
		{
			throw (UncheckedInterruptedException) t;
		}
		else if (t instanceof CompletionException)
		{
			rethrowIfIsInterruption(t.getCause());
		}
	}
	public static boolean isInterrupt(Throwable t)
	{
		Throwable unwrapped = LodUtil.ensureUnwrap(t);
		return unwrapped instanceof InterruptedException || unwrapped instanceof UncheckedInterruptedException;
	}
	
}
