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

package com.seibel.distanthorizons.core.jar;

import java.io.*;
import java.util.regex.Pattern;

/**
 * A fork of iris' dark mode detector (https://github.com/IrisShaders/Iris-Installer/blob/master/src/main/java/net/hypercubemc/iris_installer/DarkModeDetector.java)
 * Which is a fork of HanSolo's dark mode detector (https://gist.github.com/HanSolo/7cf10b86efff8ca2845bf5ec2dd0fe1d)
 *
 * This fork has better support for Linux
 *
 * @author HanSolo
 * @author IMS
 * @author coolGi
 */
public class DarkModeDetector
{
	private static final String REGQUERY_UTIL = "reg query ";
	private static final String REGDWORD_TOKEN = "REG_DWORD";
	private static final String DARK_THEME_CMD = REGQUERY_UTIL + "\"HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\"" + " /v AppsUseLightTheme";
	
	public static boolean isDarkMode()
	{
		switch (EPlatform.get())
		{
			case WINDOWS:
				return isWindowsDarkMode();
			case MACOS:
				return isMacOsDarkMode();
			case LINUX:
				// Most Unix(-like) distros also use a lot of the same things as Linux (like desktop environments and window managers)
			case BSD:
			case UNIX:
				return checkLinuxDark();
			default:
				return false;
		}
	}
	
	// Needs checking as I dont use Mac
	public static boolean isMacOsDarkMode()
	{
		boolean isDarkMode = false;
		String line = query("defaults read -g AppleInterfaceStyle");
		if (line.equals("Dark"))
		{
			isDarkMode = true;
		}
		return isDarkMode;
	}
	
	// Needs checking as I don't use Windows
	public static boolean isWindowsDarkMode()
	{
		try
		{
			String result = query(DARK_THEME_CMD);
			int p = result.indexOf(REGDWORD_TOKEN);
			
			if (p == -1)
			{
				return false;
			}
			
			// 1 == Light Mode, 0 == Dark Mode
			String temp = result.substring(p + REGDWORD_TOKEN.length()).trim();
			return ((Integer.parseInt(temp.substring("0x".length()), 16))) == 0;
		}
		catch (Exception e)
		{
			return false;
		}
	}
	
	
	// On Linux there are 2 popular formats for theming
	// They are qt and gtk. We check the desktop environment and use that to pick which one to use (if none work then use GTK)
	public static boolean checkLinuxDark()
	{
		// Checks "/usr/bin" as "echo $XDG_CURRENT_DESKTOP" dosnt work in java and dosnt detect window managers
		File de_location = new File("/usr/bin");
//        System.out.println(de_location.list());
		for (String de : de_location.list())
		{
//            System.out.println(de);
			if (de.contains("gnome-session")) // Gnome uses GTK
			{
				return GTKChecker();
			}
			if (de.contains("plasma_session")) // KDE plasma uses QT
			{
				return QTChecker();
			}
		}
		return GTKChecker(); // GTK works best with non plasma desktops (desktops includes window managers)
	}
	
	public static boolean GTKChecker()
	{
		// Checks if the return to "gsettings get org.gnome.desktop.interface color-scheme" in terminal is 'prefer-dark' or contains the word dark in it
		final Pattern darkThemeNamePattern = Pattern.compile(".*dark.*", Pattern.CASE_INSENSITIVE);
		return darkThemeNamePattern.matcher(query("gsettings get org.gnome.desktop.interface color-scheme")).matches();
	}
	
	public static boolean QTChecker()
	{
		// Get the contents of "~/.config/Trolltech.conf" then check "KWinPalette\activeBackground"
		// With that you grayscale the rgb and check if it is over/under 128
		
		// If there is a better way of doing this then please let me know
		// This seems like the best way as qt dosnt have a dark/light preference and just stores pure color values
		
		try
		{
			File themeFile = new File(System.getProperty("user.home") + "/.config/Trolltech.conf");
			
			BufferedReader reader = new BufferedReader(new FileReader(themeFile));
			String themeLine = reader.readLine();
			while (themeLine != null)
			{ // Go through each line till you find "KWinPalette\activeBackground"
				if (themeLine.contains("KWinPalette\\activeBackground"))
				{
					break;
				}
				themeLine = reader.readLine();
			}
			reader.close();
			
			// Get where the # is then read the hex numbers after it
			short index = (short) themeLine.indexOf("#");
			short r = (short) Integer.parseInt("" + themeLine.charAt(index + 1) + themeLine.charAt(index + 2), 16);
			short g = (short) Integer.parseInt("" + themeLine.charAt(index + 3) + themeLine.charAt(index + 4), 16);
			short b = (short) Integer.parseInt("" + themeLine.charAt(index + 5) + themeLine.charAt(index + 6), 16);
			if ((r + g + b) / 2 >= 128)
			{
				return false;
			}
			else
			{
				return true;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace(); return false;
		}
	}
	
	
	
	/** Runs a command trough command line */
	private static String query(String cmd)
	{
		try
		{
			Process process = Runtime.getRuntime().exec(cmd);
			StringBuilder stringBuilder = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
			{
				String actualReadLine;
				while ((actualReadLine = reader.readLine()) != null)
				{
					if (stringBuilder.length() != 0)
					{
						stringBuilder.append('\n');
					}
					stringBuilder.append(actualReadLine);
				}
			}
			return stringBuilder.toString();
		}
		catch (IOException e)
		{
			System.out.println("Exception caught while querying the OS:");
			e.printStackTrace();
			return "";
		}
	}
	
}
