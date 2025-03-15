package com.seibel.distanthorizons.common.wrappers.gui;

import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.core.config.ConfigBase;
import com.seibel.distanthorizons.core.config.gui.ConfigScreen;
import com.seibel.distanthorizons.core.config.gui.JavaScreenHandlerScreen;
import com.seibel.distanthorizons.core.config.gui.OpenGLConfigScreen;
import net.minecraft.client.gui.screens.Screen;

public class GetConfigScreen
{
	public static type useScreen = type.Classic;
	
	public enum type
	{
		Classic,
		@Deprecated
		OpenGL, // This was just an attempt, it didn't work out, and we are going to change to javafx soon (as soon as that works)
		JavaFX;
	}
	
	public static Screen getScreen(Screen parent)
	{
		// Generate the language
		// This shouldn't be here, but I need a way to test it after Minecraft inits its assets
		//System.out.println(ConfigBase.INSTANCE.generateLang(false, true));
		
		switch (useScreen)
		{
			case Classic:
				return ClassicConfigGUI.getScreen(ConfigBase.INSTANCE, parent, "client");
			case OpenGL:
				MinecraftScreen.getScreen(parent, new OpenGLConfigScreen(), ModInfo.ID + ".title");
				return null;
//            case JavaFX -> MinecraftScreen.getScreen(parent, new JavaScreenHandlerScreen(new JavaScreenHandlerScreen.ExampleScreen()), ModInfo.ID + ".title");
			case JavaFX:
				return MinecraftScreen.getScreen(parent, new JavaScreenHandlerScreen(new ConfigScreen()), ModInfo.ID + ".title");
			default:
				throw new IllegalArgumentException("No config screen implementation defined for ["+useScreen+"].");
		}
	}
	
}