package com.seibel.distanthorizons.common.wrappers.gui;

import com.seibel.distanthorizons.core.config.gui.AbstractScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;
import org.lwjglx.opengl.Display;

public class MinecraftScreen
{
	public static GuiScreen getScreen(GuiScreen parent, AbstractScreen screen, String translationName)
	{
		return new ConfigScreenRenderer(parent, screen, translationName);
	}

	private static class ConfigScreenRenderer extends DhScreen
	{
		private final GuiScreen parent;
		//private ConfigListWidget list;
		private AbstractScreen screen;


		public static String translate(String str, Object... args)
		{
			return StatCollector.translateToLocalFormatted(str, args);
		}

		protected ConfigScreenRenderer(GuiScreen parent, AbstractScreen screen, String translationName)
		{
			super(translate(translationName));
			screen.minecraftWindow = Display.getWindow();
			this.parent = parent;
			this.screen = screen;
		}

		@Override
		public void initGui()
		{
			super.initGui(); // Init Minecraft's screen
			screen.width = Display.getWidth();
			screen.height = Display.getHeight();
			screen.scaledWidth = this.width;
			screen.scaledHeight = this.height;
			screen.init(); // Init our own config screen

			/*
			this.list = new ConfigListWidget(this.minecraft, this.width, this.height, 0, 0, 25); // Select the area to tint

			#if MC_VER < MC_1_20_6 // no background is rendered in MC 1.20.6+
			if (this.minecraft != null && this.minecraft.level != null) // Check if in game
				this.list.setRenderBackground(false); // Disable from rendering
			#endif
				*/

			//this.addWidget(this.list); // Add the tint to the things to be rendered
		}

		@Override
		public void drawScreen(int mouseX, int mouseY, float delta)
		{
			this.drawDefaultBackground();
			//this.list.render(matrices, mouseX, mouseY, delta); // Renders the items in the render list (currently only used to tint background darker)

			screen.mouseX = mouseX;
			screen.mouseY = mouseY;
			screen.render(delta); // Render everything on the main screen

			super.drawScreen(mouseX, mouseY, delta); // Render the vanilla stuff (currently only used for the background and tint)
		}

		@Override
		public void setWorldAndResolution(Minecraft mc, int width, int height)
		{
			super.setWorldAndResolution(mc, width, height); // Resize Minecraft's screen
			screen.width = Display.getWidth();
			screen.height = Display.getHeight();
			screen.scaledWidth = this.width;
			screen.scaledHeight = this.height;
			screen.onResize(); // Resize our screen
		}

		@Override
		public void updateScreen()
		{
			super.updateScreen(); // Tick Minecraft's screen
			screen.tick(); // Tick our screen
			if (screen.close) // If we decide to close the screen, then actually close the screen
				onGuiClosed();
		}

		@Override
		public void onGuiClosed()
		{
			screen.onClose(); // Close our screen
			//Minecraft.getMinecraft().displayGuiScreen(this.parent); // Goto the parent screen
		}
		/*
		@Override
		public void onFilesDrop(@NotNull List<Path> files)
		{
			screen.onFilesDrop(files);
		}

		// For checking if it should close when you press the escape key
		@Override
		public boolean shouldCloseOnEsc()
		{
			return screen.shouldCloseOnEsc;
		}*/

	}
/*
	public static class ConfigListWidget extends ContainerObjectSelectionList
	{
		public ConfigListWidget(Minecraft minecraftClient, int canvasWidth, int canvasHeight, int topMargin, int botMargin, int itemSpacing)
		{
			#if MC_VER < MC_1_20_4
			super(minecraftClient, canvasWidth, canvasHeight, topMargin, canvasHeight - botMargin, itemSpacing);
			#else
			super(minecraftClient, canvasWidth, canvasHeight - (topMargin + botMargin), topMargin, itemSpacing);
			#endif
			this.centerListVertically = false;
		}

	}*/

}
