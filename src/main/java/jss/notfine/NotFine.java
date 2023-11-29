package jss.notfine;

import com.gtnewhorizons.angelica.Tags;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import jss.notfine.config.NotFineConfig;
import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = NotFine.MODID,
    name = NotFine.NAME,
    version = NotFine.VERSION,
    acceptableRemoteVersions = "*"
)
public class NotFine {
    public static final String MODID = "notfine";
    public static final String NAME = "NotFine";
    public static final String VERSION = Tags.VERSION;
    public static final Logger logger = LogManager.getLogger(NAME);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if(event.getSide() == Side.CLIENT) {
            GameSettings.Options.FRAMERATE_LIMIT.valueStep = 1f;
        }
        NotFineConfig config = new NotFineConfig();
        config.loadSettings();

        if(!NotFineConfig.allowAdvancedOpenGL) {
            Minecraft.getMinecraft().gameSettings.advancedOpengl = false;
        }

        for(Settings setting : Settings.values()) {
            setting.ready();
        }
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if(event.getSide() == Side.CLIENT) {
            SettingsManager.settingsFile.loadSettings();
        }
    }

}
