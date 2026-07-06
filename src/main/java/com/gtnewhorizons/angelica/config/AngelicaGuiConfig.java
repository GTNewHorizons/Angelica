package com.gtnewhorizons.angelica.config;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.SimpleGuiConfig;
import net.minecraft.client.gui.GuiScreen;

public class AngelicaGuiConfig extends SimpleGuiConfig {
    public AngelicaGuiConfig(GuiScreen parent) throws ConfigException {
        super(parent,
            "angelica",
            "Angelica",
            true,
            AngelicaConfig.class,
            CompatConfig.class,
            FontConfig.class
        );
    }
}
