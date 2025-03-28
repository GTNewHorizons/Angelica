package com.seibel.distanthorizons.common.wrappers.gui;

import net.minecraft.util.StatCollector;

public class GuiHelper
{
	/**
	 * Helper static methods for versional compat
	 */

	public static String TextOrLiteral(String text)
	{
       return text;
	}

	public static String TextOrTranslatable(String text)
	{
		if (StatCollector.canTranslate(text))
		{
			text = StatCollector.translateToLocal(text);
		}
        return text;
	}

	public static String Translatable(String text, Object... args)
	{
        return StatCollector.translateToLocalFormatted(text, args);
	}

}
