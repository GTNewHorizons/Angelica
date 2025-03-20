package jss.notfine.config;

import cpw.mods.fml.client.FMLClientHandler;
import jss.notfine.NotFine;
import jss.notfine.core.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.EnumSet;
import java.util.Set;

public class VideoSettings {
    private final File optionsFile;

    public VideoSettings(File optionsFile) {
        this.optionsFile = optionsFile;
    }

    public void loadSettings() {
        try {
            if (!optionsFile.exists() && !optionsFile.createNewFile()) {
                return;
            }
            BufferedReader bufferedreader = new BufferedReader(new FileReader(optionsFile));
            String settingString;
            Set<Settings> loadedSettings = EnumSet.noneOf(Settings.class);

            while((settingString = bufferedreader.readLine()) != null) {
                try {
                    String[] fragments = settingString.split(":");
                    Settings setting = Settings.valueOf(fragments[0]);
                    setting.option.deserialize(fragments[1]);
                    setting.applyChanges();
                    loadedSettings.add(setting);
                } catch (Exception exception) {
                    NotFine.logger.warn("Skipping bad option: " + settingString);
                }

            }
            bufferedreader.close();

            for (Settings setting : Settings.values()) {
                if (!loadedSettings.contains(setting)) {
                    setting.applyChanges();
                }
            }
        } catch (Exception exception) {
            NotFine.logger.error("Failed to load options", exception);
        }
    }

    public void saveSettings() {
        if (FMLClientHandler.instance().isLoading()) return;
        try {
            PrintWriter printwriter = new PrintWriter(new FileWriter(optionsFile));
            for(Settings setting : Settings.values()) {
                printwriter.println(setting.name() + ':' + setting.option.getStore());
            }
            printwriter.close();
        } catch(Exception exception) {
            NotFine.logger.error("Failed to save options", exception);
        }
    }

}
