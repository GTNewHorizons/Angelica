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

package com.seibel.distanthorizons.api.enums.config;

import org.apache.logging.log4j.Level;

/**
 * @since API 2.0.0
 * @version 2024-4-6
 */
public enum EDhApiLoggerMode
{
	DISABLED(Level.OFF, Level.OFF),
	LOG_ALL_TO_FILE(Level.ALL, Level.OFF),
	LOG_ERROR_TO_CHAT(Level.ALL, Level.ERROR),
	LOG_WARNING_TO_CHAT(Level.ALL, Level.WARN),
	LOG_INFO_TO_CHAT(Level.ALL, Level.INFO),
	LOG_DEBUG_TO_CHAT(Level.ALL, Level.DEBUG),
	LOG_ALL_TO_CHAT(Level.ALL, Level.ALL),
	LOG_ERROR_TO_CHAT_AND_FILE(Level.ERROR, Level.ERROR),
	LOG_WARNING_TO_CHAT_AND_FILE(Level.WARN, Level.WARN),
	LOG_INFO_TO_CHAT_AND_FILE(Level.INFO, Level.INFO),
	LOG_DEBUG_TO_CHAT_AND_FILE(Level.DEBUG, Level.DEBUG),
	LOG_WARNING_TO_CHAT_AND_INFO_TO_FILE(Level.INFO, Level.WARN),
	LOG_ERROR_TO_CHAT_AND_INFO_TO_FILE(Level.INFO, Level.ERROR),
	;
	
	public final Level levelForFile;
	public final Level levelForChat;
	
	EDhApiLoggerMode(Level levelForFile, Level levelForChat)
	{
		this.levelForFile = levelForFile;
		this.levelForChat = levelForChat;
	}
	
}
