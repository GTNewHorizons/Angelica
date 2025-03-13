package com.seibel.distanthorizons.core.util;

public class BoolUtil
{
	/** Used to prevent null {@link Boolean} objects in if statements */
	public static boolean falseIfNull(Boolean value) 
	{
		if (value == null)
		{
			// default to false since null doesn't mean true in any context
			// (Even in JavaScript)
			return false;
		}
		else
		{
			return value;
		}
	}
	
}
