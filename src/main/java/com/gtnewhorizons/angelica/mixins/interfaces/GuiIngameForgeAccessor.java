package com.gtnewhorizons.angelica.mixins.interfaces;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;

public interface GuiIngameForgeAccessor {
    void callRenderCrosshairs(int width, int height);

    void callRenderHelmet(ScaledResolution res, float partialTicks, boolean hasScreen, int mouseX, int mouseY);

    void callRenderPortal(int width, int height, float partialTicks);

    void callBind(ResourceLocation res);
}
