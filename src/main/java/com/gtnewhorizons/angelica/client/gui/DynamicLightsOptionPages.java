package com.gtnewhorizons.angelica.client.gui;

import com.google.common.collect.ImmutableList;
import jss.notfine.core.Settings;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.SubScreenOption;
import net.minecraft.client.resources.I18n;

public class DynamicLightsOptionPages {

    public static OptionPage dynamicLights() {
        return new OptionPage(I18n.format("options.dynamiclights.page"), ImmutableList.of(
            OptionGroup.createBuilder()
                .add(Settings.DYNAMIC_LIGHTS.option)
                .add(Settings.DYNAMIC_LIGHTS_SHADER_FORCE.option)
                .build(),
            OptionGroup.createBuilder()
                .add(Settings.DYNAMIC_LIGHTS_FRUSTUM_CULLING.option)
                .add(Settings.DYNAMIC_LIGHTS_ADAPTIVE_TICKING.option)
                .add(Settings.DYNAMIC_LIGHTS_CULL_TIMEOUT.option)
                .build(),
            OptionGroup.createBuilder()
                .add(Settings.DYNAMIC_LIGHTS_SLOW_DIST.option)
                .add(Settings.DYNAMIC_LIGHTS_SLOWER_DIST.option)
                .add(Settings.DYNAMIC_LIGHTS_BACKGROUND_DIST.option)
                .build(),
            OptionGroup.createBuilder()
                .add(new SubScreenOption(I18n.format("options.dynamiclights.entities"), I18n.format("options.dynamiclights.entities.tooltip"), DynamicLightsEntityScreen::new))
                .build()));
    }
}
