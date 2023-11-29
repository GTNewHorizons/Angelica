package com.gtnewhorizons.angelica.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

public enum AngelicaVideoSettings {
    SHADERS("options.button.shader");

    private final String unlocalizedButton;
    AngelicaVideoSettings(String unlocalizedButton) {
        this.unlocalizedButton = unlocalizedButton;
    }
    public final String getButtonLabel() {
        return I18n.format(unlocalizedButton);
    }

    public final String getTitleLabel() {
        return I18n.format("options.title." + name().toLowerCase());
    }

    public GuiButton createButton(int xPosition, int yPosition, Object setting) {
        AngelicaVideoSettings angelicaVideoSettings = (AngelicaVideoSettings) setting;
        return new GuiShadersButton(xPosition, yPosition, angelicaVideoSettings);
    }
}
