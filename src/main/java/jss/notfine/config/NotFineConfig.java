package jss.notfine.config;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;
import jss.notfine.NotFine;

import java.io.File;

public class NotFineConfig {

    public static boolean allowAdvancedOpenGL = true;

    public static final String CATEGORY_CLIENT = "client";

    private final Configuration notFineConfig;

    public NotFineConfig() {
        File configFile = new File(Launch.minecraftHome + File.separator + "config" + File.separator + NotFine.MODID + File.separator + "notfine.cfg");
        notFineConfig = new Configuration(configFile);
    }

    public void loadSettings() {
        allowAdvancedOpenGL = notFineConfig.getBoolean("allowAdvancedOpenGL", CATEGORY_CLIENT, true, "Allow or always remove the Advanced OpenGL button");

        if(notFineConfig.hasChanged()) {
            notFineConfig.save();
        }
    }

}
