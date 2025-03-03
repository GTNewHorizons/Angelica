package com.seibel.distanthorizons.common.wrappers.gui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.seibel.distanthorizons.core.config.gui.AbstractScreen;
import net.minecraft.client.Minecraft;
#if MC_VER >= MC_1_20_1
import net.minecraft.client.gui.GuiGraphics;
#endif
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;

public class MinecraftScreen
{
	public static Screen getScreen(Screen parent, AbstractScreen screen, String translationName)
	{
		return new ConfigScreenRenderer(parent, screen, translationName);
	}
	
	private static class ConfigScreenRenderer extends DhScreen
	{
		private final Screen parent;
		private ConfigListWidget list;
		private AbstractScreen screen;
		
		
		#if MC_VER < MC_1_19_2
		public static net.minecraft.network.chat.TranslatableComponent translate(String str, Object... args)
		{
			return new net.minecraft.network.chat.TranslatableComponent(str, args);
		}
		#else
		public static net.minecraft.network.chat.MutableComponent translate(String str, Object... args)
		{
			return net.minecraft.network.chat.Component.translatable(str, args);
		}
    #endif
		
		protected ConfigScreenRenderer(Screen parent, AbstractScreen screen, String translationName)
		{
			super(translate(translationName));
			screen.minecraftWindow = Minecraft.getInstance().getWindow().getWindow();
			this.parent = parent;
			this.screen = screen;
		}
		
		@Override
		protected void init()
		{
			super.init(); // Init Minecraft's screen
			Window mcWindow = this.minecraft.getWindow();
			screen.width = mcWindow.getWidth();
			screen.height = mcWindow.getHeight();
			screen.scaledWidth = this.width;
			screen.scaledHeight = this.height;
			screen.init(); // Init our own config screen
			
			this.list = new ConfigListWidget(this.minecraft, this.width, this.height, 0, 0, 25); // Select the area to tint
			
			#if MC_VER < MC_1_20_6 // no background is rendered in MC 1.20.6+
			if (this.minecraft != null && this.minecraft.level != null) // Check if in game
				this.list.setRenderBackground(false); // Disable from rendering
			#endif
			
			this.addWidget(this.list); // Add the tint to the things to be rendered
		}
		
		@Override
        #if MC_VER < MC_1_20_1
		public void render(PoseStack matrices, int mouseX, int mouseY, float delta)
        #else
		public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta)
        #endif
		{
			#if MC_VER < MC_1_20_2
			this.renderBackground(matrices); // Render background
			#else
			this.renderBackground(matrices, mouseX, mouseY, delta); // Render background
			#endif
			this.list.render(matrices, mouseX, mouseY, delta); // Renders the items in the render list (currently only used to tint background darker)
			
			screen.mouseX = mouseX;
			screen.mouseY = mouseY;
			screen.render(delta); // Render everything on the main screen
			
			super.render(matrices, mouseX, mouseY, delta); // Render the vanilla stuff (currently only used for the background and tint)
		}
		
		@Override
		public void resize(Minecraft mc, int width, int height)
		{
			super.resize(mc, width, height); // Resize Minecraft's screen
			Window mcWindow = this.minecraft.getWindow();
			screen.width = mcWindow.getWidth();
			screen.height = mcWindow.getHeight();
			screen.scaledWidth = this.width;
			screen.scaledHeight = this.height;
			screen.onResize(); // Resize our screen
		}
		
		@Override
		public void tick()
		{
			super.tick(); // Tick Minecraft's screen
			screen.tick(); // Tick our screen
			if (screen.close) // If we decide to close the screen, then actually close the screen
				onClose();
		}
		
		@Override
		public void onClose()
		{
			screen.onClose(); // Close our screen
			Objects.requireNonNull(minecraft).setScreen(this.parent); // Goto the parent screen
		}
		
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
		}
		
	}
	
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
		
	}
	
}
