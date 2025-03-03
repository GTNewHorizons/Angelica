package com.seibel.distanthorizons.common.wrappers.gui;

import net.minecraft.client.gui.Font;
#if MC_VER < MC_1_20_1
import com.mojang.blaze3d.vertex.PoseStack;
#else
import net.minecraft.client.gui.GuiGraphics;
#endif
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DhScreen extends Screen
{
	
	protected DhScreen(Component $$0)
	{
		super($$0);
	}
	
	// addRenderableWidget in 1.17 and over
	// addButton in 1.16 and below
	protected Button addBtn(Button button)
	{
		#if MC_VER < MC_1_17_1
        return this.addButton(button);
		#else
		return this.addRenderableWidget(button);
		#endif
	}
	
	#if MC_VER < MC_1_20_1
	protected void DhDrawCenteredString(PoseStack guiStack, Font font, Component text, int x, int y, int color)
	{
		drawCenteredString(guiStack, font, text, x, y, color);
	}
	protected void DhDrawString(PoseStack guiStack, Font font, Component text, int x, int y, int color)
	{
		drawString(guiStack, font, text, x, y, color);
	}
	protected void DhRenderTooltip(PoseStack guiStack, Font font, List<? extends net.minecraft.util.FormattedCharSequence> text, int x, int y)
	{
		renderTooltip(guiStack, text, x, y);
	}
	protected void DhRenderComponentTooltip(PoseStack guiStack, Font font, List<Component> comp, int x, int y)
	{
		renderComponentTooltip(guiStack, comp, x, y);
	}
	protected void DhRenderTooltip(PoseStack guiStack, Font font, Component comp, int x, int y)
	{
		renderTooltip(guiStack, comp, x, y);
	}
	#else
	protected void DhDrawCenteredString(GuiGraphics guiStack, Font font, Component text, int x, int y, int color)
	{
		guiStack.drawCenteredString(font, text, x, y, color);
	}
	protected void DhDrawString(GuiGraphics guiStack, Font font, Component text, int x, int y, int color)
	{
		guiStack.drawString(font, text, x, y, color);
	}
	protected void DhRenderTooltip(GuiGraphics guiStack, Font font, List<? extends net.minecraft.util.FormattedCharSequence> text, int x, int y)
	{
		guiStack.renderTooltip(font, text, x, y);
	}
	protected void DhRenderComponentTooltip(GuiGraphics guiStack, Font font, List<Component> comp, int x, int y)
	{
		guiStack.renderComponentTooltip(font, comp, x, y);
	}
	protected void DhRenderTooltip(GuiGraphics guiStack, Font font, Component text, int x, int y)
	{
		guiStack.renderTooltip(font, text, x, y);
	}
        #endif
}
