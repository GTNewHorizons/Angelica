package com.gtnewhorizons.angelica.mixins.interfaces;

public interface GuiIngameAccessor {
	void callRenderVignette(float brightness, int width, int height);

	void callRenderPumpkinBlur(int width, int height);

	void callRenderPortal(float partialTicks, int width, int height);
}
