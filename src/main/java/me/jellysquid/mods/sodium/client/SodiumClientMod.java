package me.jellysquid.mods.sodium.client;

import com.gtnewhorizons.angelica.Tags;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import lombok.Getter;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
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

    private static String MOD_VERSION = Tags.VERSION;

    public static final String MODID = "embeddium";
    public static final String NAME = "Embeddium";

    @Getter
    private static Thread MainThread;

    public SodiumClientMod() {
        MainThread = Thread.currentThread();

        MinecraftForge.EVENT_BUS.register(this);
//        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onInitializeClient);
//
//        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
    }


    @SubscribeEvent
    public void onGui(GuiScreenEvent.InitGuiEvent.Pre event) {
        if(event.gui instanceof GuiVideoSettings) {
            event.setCanceled(true);
            Minecraft.getMinecraft().displayGuiScreen(new SodiumOptionsGUI(((GuiVideoSettings) event.gui).parentGuiScreen));
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
        return SodiumGameOptions.load(Minecraft.getMinecraft().mcDataDir.toPath().resolve("config").resolve("rubidium-options.json"));
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
