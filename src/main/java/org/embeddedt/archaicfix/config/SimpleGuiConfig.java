package org.embeddedt.archaicfix.config;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

@SideOnly(Side.CLIENT)
public class SimpleGuiConfig extends GuiConfig {
    public SimpleGuiConfig(GuiScreen parent, Class<?> configClass, String modID, String modName) throws ConfigException {
        super(parent, ConfigurationManager.getConfigElements(configClass), modID, false, false, modName + " Configuration");
    }
}
