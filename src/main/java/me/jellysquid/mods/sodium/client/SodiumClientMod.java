package me.jellysquid.mods.sodium.client;

import com.gtnewhorizons.angelica.Tags;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = SodiumClientMod.MODID,
    name = SodiumClientMod.NAME,
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class SodiumClientMod {
    private static SodiumGameOptions CONFIG;
    public static Logger LOGGER = LogManager.getLogger("Embeddium");

    private static String MOD_VERSION;

    public static final String MODID = "embeddium";
    public static final String NAME = "Embeddium";

    public SodiumClientMod() {
//        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onInitializeClient);
//
//        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
    }

    public void onInitializeClient(final FMLInitializationEvent event) {
    	MOD_VERSION = Tags.VERSION; // ModList.get().getModContainerById(MODID).get().getModInfo().getVersion().toString();
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
//        return SodiumGameOptions.load(FMLPaths.CONFIGDIR.get().resolve("rubidium-options.json"));
        return null;
    }

    public static String getVersion() {
        if (MOD_VERSION == null) {
            throw new NullPointerException("Mod version hasn't been populated yet");
        }

        return MOD_VERSION;
    }

    public static boolean isDirectMemoryAccessEnabled() {
        return options().advanced.allowDirectMemoryAccess;
    }
}
