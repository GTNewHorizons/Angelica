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

package com.seibel.distanthorizons.coreapi.util;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Arrays;

/**
 * Miscellaneous string helper functions.
 */
public class StringUtil
{
	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	
	
	/**
	 * Returns the n-th index of the given string. <br> <br>
	 *
	 * Original source: https://stackoverflow.com/questions/3976616/how-to-find-nth-occurrence-of-character-in-a-string
	 */
	public static int nthIndexOf(String str, String substr, int n)
	{
		int pos = str.indexOf(substr);
		while (--n > 0 && pos != -1)
		{
			pos = str.indexOf(substr, pos + 1);
		}
		return pos;
	}
	
	/** @see StringUtil#join(String, Iterable)  */
	public static <T> String join(String delimiter, T[] list) { return join(delimiter, Arrays.asList(list)); }
	/** Combines each item in the given list together separated by the given delimiter. */
	public static <T> String join(String delimiter, Iterable<T> list)
	{
		StringBuilder stringBuilder = new StringBuilder();
		
		boolean firstItem = true;
		for (T item : list)
		{
			if (!firstItem)
			{
				stringBuilder.append(delimiter);
			}
			
			stringBuilder.append(item);
			firstItem = false;
		}
		
		return stringBuilder.toString();
	}
	
	/**
	 * Converts the given byte array into a hex string representation. <br>
	 * source: https://stackoverflow.com/a/9855338
	 */
	public static String byteArrayToHexString(byte[] bytes)
	{
		char[] hexChars = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++)
		{
			int v = bytes[i] & 0xFF;
			hexChars[i * 2] = HEX_ARRAY[v >>> 4];
			hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars);
	}
	
	/**
	 * Returns a shortened version of the given string that is no longer than maxLength. <br>
	 * If null returns the empty string.
	 */
	public static String shortenString(String str, int maxLength)
	{
		if (str == null)
		{
			return "";
		}
		else
		{
			return str.substring(0, Math.min(str.length(), maxLength));
		}
	}
	
	/**
	 * Source:
	 * https://stackoverflow.com/questions/3758606/how-can-i-convert-byte-size-into-a-human-readable-format-in-java#3758880
	 */
	public static String convertBytesToHumanReadable(long bytes)
	{
		if (-1000 < bytes && bytes < 1000)
		{
			return bytes + " B";
		}
		CharacterIterator ci = new StringCharacterIterator("kMGTPE");
		while (bytes <= -999_950 || bytes >= 999_950)
		{
			bytes /= 1000;
			ci.next();
		}
		return String.format("%.1f %cB", bytes / 1000.0, ci.current());
	}
	
	
	
}
