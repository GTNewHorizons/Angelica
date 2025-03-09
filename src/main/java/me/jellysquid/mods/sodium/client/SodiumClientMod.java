package me.jellysquid.mods.sodium.client;

import com.gtnewhorizons.angelica.Tags;
import com.gtnewhorizons.angelica.config.ConfigMigrator;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import lombok.Getter;
import com.gtnewhorizons.angelica.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.proxy.CommonProxy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = SodiumClientMod.MODID,
    name = SodiumClientMod.NAME,
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class SodiumClientMod {
    @SidedProxy(clientSide = "me.jellysquid.mods.sodium.proxy.ClientProxy", serverSide = "me.jellysquid.mods.sodium.proxy.CommonProxy")
    public static CommonProxy proxy;

    private static SodiumGameOptions CONFIG;
    public static Logger LOGGER = LogManager.getLogger("Embeddium");

    private static String MOD_VERSION = Tags.VERSION;

    public static final String MODID = "embeddium";
    public static final String NAME = "Embeddium";

    @Getter
    private static Thread MainThread;

    public SodiumClientMod() {
        MainThread = Thread.currentThread();
    }



    public static SodiumGameOptions options() {
        if (CONFIG == null) {
            CONFIG = loadConfig();
        }

        return CONFIG;
    }

    public static Logger logger() {
        if (LOGGER == null) {
            LOGGER = LogManager.getLogger("Embeddium");
        }

        return LOGGER;
    }

    private static SodiumGameOptions loadConfig() {
        return SodiumGameOptions.load(ConfigMigrator.handleConfigMigration("angelica-options.json"));
    }

    public static String getVersion() {
        if (MOD_VERSION == null) {
            throw new NullPointerException("Mod version hasn't been populated yet");
        }

        return MOD_VERSION;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    public static boolean isDirectMemoryAccessEnabled() {
        return options().advanced.allowDirectMemoryAccess;
    }
}
