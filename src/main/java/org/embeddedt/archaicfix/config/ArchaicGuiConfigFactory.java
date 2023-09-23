package org.embeddedt.archaicfix.config;

import net.minecraft.client.gui.GuiScreen;

public class ArchaicGuiConfigFactory implements SimpleGuiFactory {
    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return ArchaicGuiConfig.class;
    }
}