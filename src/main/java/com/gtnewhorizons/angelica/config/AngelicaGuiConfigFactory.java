package com.gtnewhorizons.angelica.config;

import net.minecraft.client.gui.GuiScreen;

public class AngelicaGuiConfigFactory implements SimpleGuiFactory {
    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return AngelicaGuiConfig.class;
    }
}
