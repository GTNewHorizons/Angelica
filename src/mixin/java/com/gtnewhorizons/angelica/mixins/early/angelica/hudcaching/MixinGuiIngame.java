package com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching;

import com.gtnewhorizons.angelica.mixins.interfaces.GuiIngameAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.GuiIngame;

@Mixin(GuiIngame.class)
public abstract class MixinGuiIngame implements GuiIngameAccessor {
	@Invoker
    public abstract void callRenderVignette(float brightness, int width, int height);

	@Invoker
    public abstract void callRenderPumpkinBlur(int width, int height);

	@Invoker("func_130015_b")
    public abstract void callRenderPortal(float partialTicks, int width, int height);
}
