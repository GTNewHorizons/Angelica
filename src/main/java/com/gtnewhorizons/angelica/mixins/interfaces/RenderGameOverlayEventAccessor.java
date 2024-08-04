package com.gtnewhorizons.angelica.mixins.interfaces;

import net.minecraft.client.gui.ScaledResolution;

public interface RenderGameOverlayEventAccessor {
    void setPartialTicks(float value);

    void setResolution(ScaledResolution resolution);

    void setMouseX(int mouseX);

    void setMouseY(int mouseY);
}
