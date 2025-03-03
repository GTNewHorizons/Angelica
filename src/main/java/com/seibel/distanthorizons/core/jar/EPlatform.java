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

/**
 * A simple OS getting util based off LWJGL's EPlatform at {@link org.lwjgl.system.Platform}. <br>
 * This version includes some extra utils that we need and removes stuff which we don't.
 *
 * @author coolGi
 */
public enum EPlatform
{
	WINDOWS("Windows", false),
	LINUX("Linux", true),
	MACOS("macOS", true),
	BSD("BSD", true),
	UNIX("Unix", true);
	
	public enum EArchitecture
	{
		X86(false),
		X64(true),
		ARM32(false),
		ARM64(true);
		
		static final EArchitecture current;
		final boolean is64Bit;
		
		static
		{
			String osArch = System.getProperty("os.arch");
			boolean is64Bit = osArch.contains("64") || osArch.startsWith("armv8");
			
			current = osArch.startsWith("arm") || osArch.startsWith("aarch64")
					? (is64Bit ? ARM64 : ARM32)
					: (is64Bit ? X64 : X86);
		}
		
		EArchitecture(boolean is64Bit)
		{
			this.is64Bit = is64Bit;
		}
	}
	
	private static final EPlatform current;
	
	static
	{
		String osName = System.getProperty("os.name").toLowerCase();
		if (osName.contains("windows"))
		{ // Proper name is "Windows"
			current = WINDOWS;
		}
		else if (osName.contains("linux"))
		{ // Proper name is "Linux"
			current = LINUX;
		}
		else if (osName.contains("mac") || osName.contains("darwin"))
		{ // For MacOS it should either output "Mac OS X" or "Darwin" depending on the version of MacOS
			current = MACOS;
		}
		else if (osName.startsWith("bsd"))
		{ // Depending on the BSD distro this will be different
			current = BSD;
		}
		else if (osName.startsWith("unix"))
		{ // In case you are running some very niece OS which didnt change their OS name from Unix
			current = UNIX;
		}
		else
		{
			// We only test on Windows, Linux and MacOS
			// If you use a different os (eg, BSD, Unix) then it may or may not work
			// Otherwise, good luck
			throw new LinkageError("Unknown platform: " + osName);
		}
	}
	
	private final String name;
	private final boolean isUnix;
	
	EPlatform(String name, boolean isUnix)
	{
		this.name = name;
		this.isUnix = isUnix;
	}
	
	/** Returns the platform name. */
	public String getName()
	{
		return name;
	}
	
	/** Returns weather the OS is Unix or Unix-Like OS */
	public boolean isUnix()
	{
		return isUnix;
	}
	
	/** Returns the platform on which the library is running. */
	public static EPlatform get()
	{
		return current;
	}
	
	/** Returns the architecture on which the library is running. */
	public static EArchitecture getArchitecture()
	{
		return EArchitecture.current;
	}
	
	
	@Override
	public String toString()
	{
		return this.getName();
	}
}
