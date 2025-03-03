package com.seibel.distanthorizons.common.wrappers.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

#if MC_VER < MC_1_19_2
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
#endif

public class GuiHelper
{
	/**
	 * Helper static methods for versional compat
	 */
	public static Button MakeBtn(Component base, int posX, int posZ, int width, int height, Button.OnPress action)
	{
        #if MC_VER < MC_1_19_4
		return new Button(posX, posZ, width, height, base, action);
        #else
		return Button.builder(base, action).bounds(posX, posZ, width, height).build();
        #endif
	}
	
	public static MutableComponent TextOrLiteral(String text)
	{
        #if MC_VER < MC_1_19_2
		return new TextComponent(text);
        #else
		return Component.literal(text);
        #endif
	}
	
	public static MutableComponent TextOrTranslatable(String text)
	{
        #if MC_VER < MC_1_19_2
		return new TextComponent(text);
        #else
		return Component.translatable(text);
        #endif
	}
	
	public static MutableComponent Translatable(String text, Object... args)
	{
        #if MC_VER < MC_1_19_2
		return new TranslatableComponent(text, args);
        #else
		return Component.translatable(text, args);
        #endif
	}
	
	public static void SetX(AbstractWidget w, int x)
	{
        #if MC_VER < MC_1_19_4
		w.x = x;
        #else
		w.setX(x);
        #endif
	}
	
	public static void SetY(AbstractWidget w, int y)
	{
        #if MC_VER < MC_1_19_4
		w.y = y;
        #else
		w.setY(y);
        #endif
	}
	
}
