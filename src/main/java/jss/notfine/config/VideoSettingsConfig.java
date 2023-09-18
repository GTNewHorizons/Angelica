package jss.notfine.config;

import jss.notfine.core.Settings;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class VideoSettingsConfig {
    private final Configuration settingsConfig;

    public VideoSettingsConfig(File userSettingsFile) {
        settingsConfig = new Configuration(userSettingsFile);
    }

    public void loadSettings() {
        for(Settings setting : Settings.values()) {

            float value = settingsConfig.getFloat(
                setting.name(),
                ("Settings"),
                setting.base,
                setting.minimum,
                setting.maximum,
                setting.configComment
            );

            if(setting.step > 0f) {
                value = setting.step * (float)Math.round(value / setting.step);
            }

            setting.setValue(value);
        }
        if(settingsConfig.hasChanged()) {
            settingsConfig.save();
        }
    }

    public void saveSettings() {
        for(Settings setting : Settings.values()) {
            settingsConfig.get(
                ("Settings"),
                setting.name(),
                setting.base,
                setting.configComment,
                setting.minimum,
                setting.maximum
            ).set(setting.getValue());
        }
        settingsConfig.save();
    }

}
