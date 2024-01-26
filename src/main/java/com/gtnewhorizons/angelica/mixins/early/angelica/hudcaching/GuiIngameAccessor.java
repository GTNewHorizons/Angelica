package com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.GuiIngame;

@Mixin(GuiIngame.class)
public interface GuiIngameAccessor {
	@Invoker
	void callRenderVignette(float brightness, int width, int height);
	
	@Invoker
	void callRenderPumpkinBlur(int width, int height);
	
	@Invoker("func_130015_b")
	void callRenderPortal(float partialTicks, int width, int height);
}
