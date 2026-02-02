package me.jellysquid.mods.sodium.client.gui.options.storage;

import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.config.AngelicaConfig;

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
        AngelicaMod.LOGGER.info("Flushed changes to Angelica configuration");
    }
}
