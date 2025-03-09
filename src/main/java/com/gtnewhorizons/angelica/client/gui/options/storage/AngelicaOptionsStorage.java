package com.gtnewhorizons.angelica.client.gui.options.storage;

import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import me.jellysquid.mods.sodium.client.SodiumClientMod;

public class AngelicaOptionsStorage implements OptionStorage<AngelicaConfig> {

    public AngelicaOptionsStorage() {

    }

    @Override
    public AngelicaConfig getData() {
        return null;
    }

    @Override
    public void save() {
        ConfigurationManager.save(AngelicaConfig.class);
        SodiumClientMod.logger().info("Flushed changes to Angelica configuration");
    }
}
