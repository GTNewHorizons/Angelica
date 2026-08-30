package jss.notfine.config;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;
import jss.notfine.NotFine;

import java.io.File;

public class NotFineConfig {

    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_TOGGLE = "toggle";
    private static final String CATEGORY_UNFINISHED = "unfinished";

    public static boolean allowToggle3DAnaglyph;

    public static boolean betterBlockFaceCulling;
    public static boolean renderPass;

    public static void loadSettings() {
        File configFile = new File(Launch.minecraftHome + File.separator + "config" + File.separator + NotFine.MODID + File.separator + "notfine.cfg");
        Configuration config = new Configuration(configFile);

        allowToggle3DAnaglyph = config.getBoolean("allowToggle3DAnaglyph", CATEGORY_GENERAL, true, "Allow 3D Anaglyph to be enabled.");

        config.setCategoryComment(CATEGORY_TOGGLE, "Toggle mod features.");
        betterBlockFaceCulling = config.getBoolean("betterBlockFaceCulling", CATEGORY_TOGGLE, true,
            "Use more accurate block face culling when building chunk meshes.");
        config.setCategoryComment(CATEGORY_TOGGLE, "Enable or disable various mod features.");

        config.setCategoryComment(CATEGORY_UNFINISHED, "Toggle mod features that are unfinished or require compatibility improvements.");
        renderPass = config.getBoolean("renderPass", CATEGORY_UNFINISHED, false,
            "Allows resource pack artists to add block textures to be rendered during additional passes.");

        if(config.hasChanged()) {
            config.save();
        }

        //This will probably break hard with Angelica.
        renderPass = false;
    }

}
