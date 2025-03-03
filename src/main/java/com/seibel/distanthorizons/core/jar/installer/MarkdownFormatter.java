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

package com.seibel.distanthorizons.core.jar.installer;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Formats Markdown to other markup languages
 *
 * @author coolGi
 */
public class MarkdownFormatter
{
	public static abstract class GenericFormat
	{
		/** Converts Markdown text to a different format */
		public String convertFrom(String original)
		{
			System.out.println("Function \"convertFrom\" for \"" + this.getClass().getSimpleName() + "\" not done yet");
			return null;
		}
		/** Converts formatted text back to Markdown */
		public String convertTo(String original)
		{
			System.out.println("Function \"convertTo\" for \"" + this.getClass().getSimpleName() + "\" not done yet");
			return null;
		}
		
	}
	
	
	/** Split a string every n characters */
	public static List<String> splitString(String text, int n)
	{
		return Arrays.asList(
				text.split("(?<=\\G.{" + n + "})")
		);
	}
	private static String replaceRegex(String text, String regex, String start, String end)
	{
		while (Pattern.compile(regex).matcher(text).find())
		{
			text = text.replaceFirst(regex, start).replaceFirst(regex, end);
		}
		return text;
	}
	
	
	
	
	public static class HTMLFormat extends GenericFormat
	{
		@Override
		public String convertTo(String original)
		{
			original = original.replaceAll("\\\\\\n", "<br>") // Removes the "\" used in markdown to create new line
					.replaceAll("\\n", "<br>"); // Fix the new line
			
			original = replaceRegex(original, "\\*\\*", "<b>", "</b>"); // Makes the text bold
			original = replaceRegex(original, "~~", "<del>", "</del>"); // Striketrough
//            original = replaceRegex(original, "_", "<i>", "</i>"); // Italic
			
			return original;
		}
		
	}
	
	public static class MinecraftFormat extends GenericFormat
	{
		@Override
		public String convertTo(String original)
		{
			original = original.replaceAll("<br>|<br/>", "\n"); // New lines
			original = replaceRegex(original, "\\*\\*", "§l", "§r"); // Bold
			original = replaceRegex(original, "~~", "§m", "§r"); // Striketrough
//            original = replaceRegex(original, "_", "§n", "§r"); // Italic
			return original;
		}
		
	}
	
}
