package com.gtnewhorizons.angelica.config;

import net.minecraft.client.gui.GuiScreen;

public class AngelicaGuiConfig extends SimpleGuiConfig {
    public AngelicaGuiConfig(GuiScreen parent) throws ConfigException {
        super(parent, AngelicaConfig.class, "angelica", "Angelica");
    }
}
