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

package com.seibel.distanthorizons.core.util;

import java.io.InvalidObjectException;

/**
 * Methods related to handling and using enums.
 *
 * @author James Seibel
 * @version 2022-6-30
 */
public class EnumUtil
{
	/**
	 * Attempts to find the enum value with the given name
	 * (ignoring case).
	 *
	 * @param enumName the string value to parse
	 * @param enumType The class of the enum of parse
	 * @param <T> The Enum type to parse
	 * @throws InvalidObjectException if no enum exists with the given enumName
	 */
	public static <T extends Enum<T>> T parseEnumIgnoreCase(String enumName, Class<T> enumType) throws InvalidObjectException
	{
		// attempt to find an enum with enumName
		for (T enumValue : enumType.getEnumConstants())
		{
			if (enumValue.name().equalsIgnoreCase(enumName))
			{
				return enumValue;
			}
		}
		
		// no enum found
		throw new InvalidObjectException("No Enum of type [" + enumType.getSimpleName() + "] exists with the name [" + enumName + "]. Possible enum values are: [" + createEnumCsv(enumType) + "]");
	}
	
	
	
	/**
	 * Returns a comma separated list of all possible enum values
	 * for the given enumType. <Br><Br>
	 *
	 * Example output: <Br>
	 * "NEAR, FAR, NEAR_AND_FAR"
	 */
	public static String createEnumCsv(Class<? extends Enum<?>> enumType)
	{
		StringBuilder str = new StringBuilder();
		Enum<?>[] enumValues = enumType.getEnumConstants();
		
		for (int i = 0; i < enumValues.length; i++)
		{
			if (i == 0)
			{
				// the first value doesn't need a comma
				str.append(enumValues[i].name());
			}
			else
			{
				str.append(", ").append(enumValues[i].name());
			}
		}
		
		return str.toString();
	}
	
	
	
	/** Returns true if both enums contain the same values. */
	public static EnumComparisonResult compareEnumClassesByValues(Class<? extends Enum<?>> alphaEnum, Class<? extends Enum<?>> betaEnum)
	{
		Enum<?>[] alphaValues = alphaEnum.getEnumConstants();
		Enum<?>[] betaValues = betaEnum.getEnumConstants();
		
		// compare the number of enum values
		if (alphaValues.length != betaValues.length)
		{
			return new EnumComparisonResult(false, createFailMessageHeader(alphaEnum, betaEnum) + "the enums have [" + alphaValues.length + "] and [" + betaValues.length + "] values respectively.");
		}
		
		// check that each value exists in both enums
		for (Enum<?> alphaVal : alphaValues)
		{
			boolean valueFoundInBothEnums = false;
			for (Enum<?> betaVal : betaValues)
			{
				if (alphaVal.name().equals(betaVal.name()))
				{
					valueFoundInBothEnums = true;
					break;
				}
			}
			
			if (!valueFoundInBothEnums)
			{
				// an enum value wasn't found
				return new EnumComparisonResult(false, createFailMessageHeader(alphaEnum, betaEnum) + "the enum value [" + alphaVal.name() + "] wasn't found in [" + betaEnum.getSimpleName() + "].");
			}
		}
		
		// every enum value is the same
		return new EnumComparisonResult(true, "");
	}
	/** helper method */
	public static String createFailMessageHeader(Class<? extends Enum<?>> alphaEnum, Class<? extends Enum<?>> betaEnum)
	{
		return "The enums [" + alphaEnum.getSimpleName() + "] and [" + betaEnum.getSimpleName() + "] aren't equal: ";
	}
	/** helper object */
	public static class EnumComparisonResult
	{
		public final boolean success;
		public final String failMessage;
		
		public EnumComparisonResult(boolean newSuccess, String newFailMessage)
		{
			this.success = newSuccess;
			this.failMessage = newFailMessage;
		}
		
	}
	
}