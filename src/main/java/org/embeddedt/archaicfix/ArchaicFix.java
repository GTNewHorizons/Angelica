package org.embeddedt.archaicfix;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLConstructionEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.common.MinecraftForge;
import org.embeddedt.archaicfix.proxy.CommonProxy;

@Mod(modid = ArchaicFix.MODID, version = ArchaicFix.VERSION, dependencies = "required-after:gtnhmixins@[2.0.0,);", guiFactory = "org.embeddedt.archaicfix.config.ArchaicGuiConfigFactory")
public class ArchaicFix
{
    public static final String MODID = "archaicfix";
    public static final String VERSION = "ArchaicFix";

    private FixHelper helper;

    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    @SidedProxy(clientSide = "org.embeddedt.archaicfix.proxy.ClientProxy", serverSide = "org.embeddedt.archaicfix.proxy.CommonProxy")
    public static CommonProxy proxy;

    @EventHandler
    public void onConstruct(FMLConstructionEvent event) {
        try {
            Class.forName("com.gildedgames.util.threadedlighting.asm.TLTransformer");
            ArchaicLogger.LOGGER.fatal("=================== WARNING ===================");
            ArchaicLogger.LOGGER.fatal("A version of GG Util that includes threaded lighting was detected. ArchaicFix has prevented launching to avoid issues. Please download a fixed version of GG Util: https://www.curseforge.com/minecraft/mc-mods/gilded-game-utils-fix.");
            ArchaicLogger.LOGGER.fatal("===============================================");
            throw new UnsupportedOperationException("Please download a fixed version of GG Util: https://www.curseforge.com/minecraft/mc-mods/gilded-game-utils-fix");
        } catch(ClassNotFoundException ignored) {}
    }

    @EventHandler
    public void preinit(FMLPreInitializationEvent event) {
        helper = new FixHelper();
        MinecraftForge.EVENT_BUS.register(helper);
        FMLCommonHandler.instance().bus().register(helper);
        proxy.preinit();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
    }

    @EventHandler
    public void serverStart(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandDebugUpdateQueue());
    }

    @EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        proxy.loadcomplete();
    }

}
