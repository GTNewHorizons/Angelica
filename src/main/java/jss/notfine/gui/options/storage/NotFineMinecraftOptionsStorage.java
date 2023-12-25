package jss.notfine.gui.options.storage;

import jss.notfine.NotFine;
import jss.notfine.core.SettingsManager;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;

public class NotFineMinecraftOptionsStorage implements OptionStorage<OptionStorageDummy> {

    private final OptionStorageDummy dummy = new OptionStorageDummy();

    @Override
    public OptionStorageDummy getData() {
        return dummy;
    }

    @Override
    public void save() {
        SettingsManager.settingsFile.saveSettings();

        NotFine.logger.info("Flushed changes to NotFine configuration");
    }

}
