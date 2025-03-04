package com.seibel.distanthorizons.common.wrappers.gui;

import com.seibel.distanthorizons.core.wrapperInterfaces.config.ILangWrapper;

public class LangWrapper implements ILangWrapper
{
	public static final LangWrapper INSTANCE = new LangWrapper();
	@Override
	public boolean langExists(String str)
	{
		return false; // TODO
	}

	@Override
	public String getLang(String str)
	{
		return "<TODO!>"; // TODO
	}

}
