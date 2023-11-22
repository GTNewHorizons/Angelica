package com.gtnewhorizons.angelica.client.gui;

import cpw.mods.fml.client.config.GuiSlider;
import jss.notfine.gui.ISettingsEnum;
import net.minecraft.client.resources.I18n;

import static jss.notfine.core.Settings.RENDER_DISTANCE_ENTITIES;

public enum AngelicaEntityRenderDistanceSetting implements ISettingsEnum {

    ENTITY_RENDER_DISTANCE("options.button.entityRenderDistance");
    private final String unlocalizedButton;

    AngelicaEntityRenderDistanceSetting(String unlocalizedButton) {
        this.unlocalizedButton = unlocalizedButton;
    }

    @Override
    public java.lang.String getButtonLabel() {
        return I18n.format(unlocalizedButton);
    }

    @Override
    public String getTitleLabel() {
        return null;
    }

    public GuiEntityRenderDistanceSlider createButton(int xPosition, int yPosition, Object setting) {
        return new GuiEntityRenderDistanceSlider(xPosition, yPosition, RENDER_DISTANCE_ENTITIES);
    }
}
