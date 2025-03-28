package com.seibel.distanthorizons.common.wrappers.gui;

import com.seibel.distanthorizons.core.wrapperInterfaces.config.ILangWrapper;
import net.minecraft.util.StatCollector;

public class LangWrapper implements ILangWrapper
{
	public static final LangWrapper INSTANCE = new LangWrapper();
	@Override
	public boolean langExists(String str)
	{
		return StatCollector.canTranslate(str);
	}

	@Override
	public String getLang(String str)
	{
		return StatCollector.translateToLocal(str);
	}

}
