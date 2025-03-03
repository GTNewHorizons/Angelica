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

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.coreapi.ModInfo;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.function.Function;

/**
 * Handles parsing and creating string messages from OpenGL messages.
 *
 * @author Leetom
 * @version 2022-10-1
 */
public final class GLMessage
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());
	
	static final String HEADER = "[LWJGL] OpenGL debug message";
	public final EType type;
	public final ESeverity severity;
	public final ESource source;
	public final String id;
	public final String message;
	
	/** This is needed since gl callback will not have the correct class loader set, which causes issues. */
	static void initLoadClass()
	{
		Builder dummy = new Builder();
		dummy.add(GLMessage.HEADER);
		dummy.add("ID");
		dummy.add(":");
		dummy.add("dummyId");
		dummy.add("Source");
		dummy.add(":");
		dummy.add(ESource.API.name);
		dummy.add("Type");
		dummy.add(":");
		dummy.add(EType.OTHER.name);
		dummy.add("Severity");
		dummy.add(":");
		dummy.add(ESeverity.LOW.name);
		dummy.add("Message");
		dummy.add(":");
		dummy.add("dummyMessage");
	}
	
	static
	{
		initLoadClass();
	}
	
	
	
	GLMessage(EType type, ESeverity severity, ESource source, String id, String message)
	{
		this.type = type;
		this.source = source;
		this.severity = severity;
		this.id = id;
		this.message = message;
	}
	
	@Override
	public String toString() { return "[level:" + severity + ", type:" + type + ", source:" + source + ", id:" + id + ", msg:{" + message + "}]"; }
	
	
	
	//==============//
	// helper enums //
	//==============//
	
	public enum EType
	{
		ERROR,
		DEPRECATED_BEHAVIOR,
		UNDEFINED_BEHAVIOR,
		PORTABILITY,
		PERFORMANCE,
		MARKER,
		PUSH_GROUP,
		POP_GROUP,
		OTHER;
		
		
		private static final HashMap<String, EType> ENUM_BY_NAME = new HashMap<>();
		
		private final String name;
		
		
		static
		{
			for (EType type : EType.values())
			{
				ENUM_BY_NAME.put(type.name, type);
			}
		}
		
		EType() { name = super.toString().toUpperCase(); }
		
		
		@Override
		public final String toString() { return name; }
		
		public static EType get(String name) { return ENUM_BY_NAME.get(name.toUpperCase()); }
		
	}
	
	public enum ESource
	{
		API,
		WINDOW_SYSTEM,
		SHADER_COMPILER,
		THIRD_PARTY,
		APPLICATION,
		OTHER;
		
		
		private static final HashMap<String, ESource> ENUM_BY_NAME = new HashMap<>();
		
		public final String name;
		
		
		static
		{
			for (ESource source : ESource.values())
			{
				ENUM_BY_NAME.put(source.name, source);
			}
		}
		
		ESource() { name = super.toString().toUpperCase(); }
		
		
		@Override
		public final String toString() { return name; }
		
		public static ESource get(String name) { return ENUM_BY_NAME.get(name.toUpperCase()); }
		
	}
	
	public enum ESeverity
	{
		HIGH,
		MEDIUM,
		LOW,
		NOTIFICATION;
		
		
		public final String name;
		
		static final HashMap<String, ESeverity> ENUM_BY_NAME = new HashMap<>();
		
		
		static
		{
			for (ESeverity severity : ESeverity.values())
			{
				ENUM_BY_NAME.put(severity.name, severity);
			}
		}
		
		ESeverity() { name = super.toString().toUpperCase(); }
		
		
		@Override
		public final String toString() { return name; }
		
		public static ESeverity get(String name) { return ENUM_BY_NAME.get(name.toUpperCase()); }
		
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	/**
	 * Expected message format: <br>
	 * <code>
	 * [LWJGL] OpenGL debug message      <br>
	 * ID: 0x20071                       <br>
	 * Source: API                       <br>
	 * Type: OTHER                       <br>
	 * Severity: NOTIFICATION            <br>
	 * Message: Buffer detailed info: Buffer object 1014084 (bound to ...
	 * </code>
	 */
	public static class Builder
	{
		/** how many stages are present in the message parser */
		private static final int FINAL_PARSER_STAGE_INDEX = 15;
		
		public static final Builder DEFAULT_MESSAGE_BUILDER =
			new Builder(
					(type) -> 
					{ // type filter
						if (type == GLMessage.EType.POP_GROUP)
							return false;
						if (type == GLMessage.EType.PUSH_GROUP)
							return false;
						if (type == GLMessage.EType.MARKER)
							return false;
						// if (type == GLMessage.Type.PERFORMANCE) return false;
						return true;
					},
					(severity) -> 
					{ // severity filter
						if (severity == GLMessage.ESeverity.NOTIFICATION)
							return false;
						return true;
					},
					null
			);
		
		
		private final StringBuilder inProgressMessageBuilder = new StringBuilder();
		
		private EType type;
		private ESeverity severity;
		private ESource source;
		
		/** if the function returns false the message will be allowed */
		private final Function<EType, Boolean> typeFilter;
		/** if the function returns false the message will be allowed */
		private final Function<ESeverity, Boolean> severityFilter;
		/** if the function returns false the message will be allowed */
		private final Function<ESource, Boolean> sourceFilter;
		
		private String id;
		private String message;
		/** how far into the message parser this builder is */
		private int parserStage = 0;
		
		
		
		static
		{
			initLoadClass();
		}
		
		public Builder() { this(null, null, null); }
		
		public Builder(
				Function<EType, Boolean> typeFilter,
				Function<ESeverity, Boolean> severityFilter,
				Function<ESource, Boolean> sourceFilter)
		{
			this.typeFilter = typeFilter;
			this.severityFilter = severityFilter;
			this.sourceFilter = sourceFilter;
		}
		
		
		
		/**
		 * Adds the given string to the message builder. <br> <br>
		 *
		 * Will log a warning if the string given wasn't expected
		 * for the next stage of the OpenGL message format.<br> <br>
		 *
		 * @return null if the message isn't complete
		 */
		public GLMessage add(String str)
		{
			// TODO fix implementation for MC 1.20.2 and newer
			//  please see the incomplete GLMessageTest for an example as to how the message formats differ
			if (true)
				return null;
			
			str = str.trim();
			if (str.isEmpty())
				return null;
			
			boolean parseSuccess = runNextParserStage(str);
			if (parseSuccess && parserStage > FINAL_PARSER_STAGE_INDEX)
			{
				this.parserStage = 0;
				GLMessage msg = new GLMessage(this.type, this.severity, this.source, this.id, this.message);
				if (doesMessagePassFilters(msg))
				{
					return msg;
				}
			}
			else if (!parseSuccess)
			{
				LOGGER.warn("Failed to parse GLMessage line '{}' at stage {}", str, parserStage);
			}
			
			// the message isn't finished yet
			return null;
			
			// TODO implement a method that works for both MC 1.20.2+ and 1.20.1-
			//if (str.equals(HEADER) && inProgressMessageBuilder.length() != 0)
			//{
			//	boolean parseSuccess = runNextParserStage(str);
			//	if (parseSuccess && parserStage > FINAL_PARSER_STAGE_INDEX)
			//	{
			//		this.parserStage = 0;
			//		GLMessage msg = new GLMessage(this.type, this.severity, this.source, this.id, this.message);
			//		if (doesMessagePassFilters(msg))
			//		{
			//			return msg;
			//		}
			//		else
			//		{
			//			inProgressMessageBuilder.setLength(0);
			//			return null;
			//		}
			//	}
			//	else
			//	{
			//		if (!parseSuccess)
			//		{
			//			LOGGER.warn("Failed to parse GLMessage line '{}' at stage {}", str, parserStage);
			//			inProgressMessageBuilder.setLength(0);
			//		}
			//		
			//		return null;
			//	}
			//}
			//else
			//{
			//	inProgressMessageBuilder.append(str);
			//	return null;
			//}
		}
		
		private boolean doesMessagePassFilters(GLMessage msg)
		{
			if (this.sourceFilter != null && !this.sourceFilter.apply(msg.source))
				return false;
			else if (this.typeFilter != null && !this.typeFilter.apply(msg.type))
				return false;
			else if (this.severityFilter != null && !this.severityFilter.apply(msg.severity))
				return false;
			else
				return true;
		}
		
		/** @return true if the given string was expected next for the OpenGL message format */
		private boolean runNextParserStage(String str)
		{
			switch (this.parserStage)
			{
				case 0:
					return checkAndIncStage(str, GLMessage.HEADER);
				case 1:
					return checkAndIncStage(str, "ID");
				case 2:
					return checkAndIncStage(str, ":");
				case 3:
					this.id = str;
					this.parserStage++;
					return true;
				case 4:
					return checkAndIncStage(str, "Source");
				case 5:
					return checkAndIncStage(str, ":");
				case 6:
					this.source = ESource.get(str);
					this.parserStage++;
					return true;
				case 7:
					return checkAndIncStage(str, "Type");
				case 8:
					return checkAndIncStage(str, ":");
				case 9:
					this.type = EType.get(str);
					this.parserStage++;
					return true;
				case 10:
					return checkAndIncStage(str, "Severity");
				case 11:
					return checkAndIncStage(str, ":");
				case 12:
					this.severity = ESeverity.get(str);
					this.parserStage++;
					return true;
				case 13:
					return checkAndIncStage(str, "Message");
				case 14:
					return checkAndIncStage(str, ":");
				case 15:
					this.message = str;
					this.parserStage++;
					return true;
				default:
					return false;
			}
		}
		
		/**
		 * Returns true and increments the parserStage
		 * if the given and expected strings are the same.
		 */
		private boolean checkAndIncStage(String givenString, String expectedString)
		{
			boolean equal = givenString.equals(expectedString);
			//boolean equal = givenString.contains(expectedString);
			if (equal)
				this.parserStage++;
			return equal;
		}
		
	} // builder class
	
}