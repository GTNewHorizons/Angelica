package me.jellysquid.mods.sodium.client;

import me.jellysquid.mods.sodium.client.compat.ccl.CCLCompat;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SodiumClientMod.MODID)
public class SodiumClientMod {
    private static SodiumGameOptions CONFIG;
    public static Logger LOGGER = LogManager.getLogger("Embeddium");

    private static String MOD_VERSION;

    public static final String MODID = "embeddium";

    public static boolean flywheelLoaded;
    public static boolean cclLoaded;

    public SodiumClientMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onInitializeClient);

        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
    }

    public void onInitializeClient(final FMLClientSetupEvent event) {
    	flywheelLoaded = ModList.get().isLoaded("flywheel");
    	cclLoaded = ModList.get().isLoaded("codechickenlib");
    	MOD_VERSION = ModList.get().getModContainerById(MODID).get().getModInfo().getVersion().toString();

    	if(cclLoaded) {
    		CCLCompat.init();
    	}
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
        return SodiumGameOptions.load(FMLPaths.CONFIGDIR.get().resolve("rubidium-options.json"));
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
