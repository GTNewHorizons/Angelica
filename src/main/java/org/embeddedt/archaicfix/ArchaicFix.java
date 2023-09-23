package org.embeddedt.archaicfix;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraftforge.common.MinecraftForge;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.embeddedt.archaicfix.ducks.IAcceleratedRecipe;
import org.embeddedt.archaicfix.threadedupdates.ThreadedChunkUpdateHelper;
import org.embeddedt.archaicfix.proxy.CommonProxy;
import thaumcraft.api.ThaumcraftApi;

import java.util.*;

@Mod(modid = ArchaicFix.MODID, version = ArchaicFix.VERSION, dependencies = "required-after:gtnhmixins@[2.0.0,);", guiFactory = "org.embeddedt.archaicfix.config.ArchaicGuiConfigFactory")
public class ArchaicFix
{
    public static final String MODID = "archaicfix";
    public static final String VERSION = "ArchaicFix";

    private FixHelper helper;

    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("archaicfix");
    public static volatile boolean IS_VANILLA_SERVER = false;
    public static boolean NEI_INSTALLED = false;

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
        } catch(ClassNotFoundException e) {

        }
    }

    @EventHandler
    public void preinit(FMLPreInitializationEvent event)
    {
        int nextID = ReflectionHelper.getPrivateValue(Entity.class, null, "field_70152_a", "nextEntityID");
        if(nextID == 0) {
            ReflectionHelper.setPrivateValue(Entity.class, null, 1, "field_70152_a", "nextEntityID");
            ArchaicLogger.LOGGER.info("Fixed MC-111480");
        }
        MinecraftForge.EVENT_BUS.register(new LeftClickEventHandler());
        helper = new FixHelper();
        MinecraftForge.EVENT_BUS.register(helper);
        FMLCommonHandler.instance().bus().register(helper);
        proxy.preinit();
        NEI_INSTALLED = Loader.isModLoaded("NotEnoughItems");
        //SoundSystemConfig.setNumberNormalChannels(1073741824);
        //SoundSystemConfig.setNumberStreamingChannels(1073741823);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        if(event.getSide() == Side.CLIENT) {
            if(ArchaicConfig.enableThreadedChunkUpdates
                    && ArchaicConfig.enableOcclusionTweaks) {
                ThreadedChunkUpdateHelper.instance = new ThreadedChunkUpdateHelper();
                ThreadedChunkUpdateHelper.instance.init();
            }
        }
    }

    @EventHandler
    public void serverStart(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandDebugUpdateQueue());
    }

    private void printRecipeDebug() {
        if(!ArchaicConfig.cacheRecipes)
            return;
        HashMap<Class<? extends IRecipe>, Integer> recipeTypeMap = new HashMap<>();
        for(Object o : CraftingManager.getInstance().getRecipeList()) {
            recipeTypeMap.compute(((IRecipe)o).getClass(), (key, oldValue) -> {
                if(oldValue == null)
                    return 1;
                else
                    return oldValue + 1;
            });
        }
        recipeTypeMap.entrySet().stream()
                .sorted(Comparator.comparingInt(pair -> pair.getValue()))
                .forEach(pair -> {
                    String acceleratedSuffix = IAcceleratedRecipe.class.isAssignableFrom(pair.getKey()) ? " (accelerated)" : "";
                    ArchaicLogger.LOGGER.info("There are " + pair.getValue() + " recipes of type " + pair.getKey().getName() + acceleratedSuffix);
                });
        int totalRecipes = recipeTypeMap.values().stream().reduce(0, Integer::sum);
        int acceleratedRecipes = recipeTypeMap.entrySet().stream().filter(pair -> IAcceleratedRecipe.class.isAssignableFrom(pair.getKey())).map(Map.Entry::getValue).reduce(0, Integer::sum);
        ArchaicLogger.LOGGER.info(acceleratedRecipes + " / " + totalRecipes + " recipes are accelerated!");
    }

    private void removeThaumcraftLeak() {
        if(!Loader.isModLoaded("Thaumcraft")) {
            boolean thaumcraftGhostApiPresent = false;
            try {
                Class.forName("thaumcraft.api.ThaumcraftApi");
                thaumcraftGhostApiPresent = true;
            } catch(Exception e) {

            }
            if(thaumcraftGhostApiPresent) {
                try {
                    ArchaicLogger.LOGGER.info("Cleared " + ThaumcraftApi.objectTags.size() + " unused Thaumcraft aspects");
                    ThaumcraftApi.objectTags.clear();
                } catch (IncompatibleClassChangeError e) {
                    ArchaicLogger.LOGGER.info("Thaumcraft does not have an objectTags field");
                }
                try {
                    ArchaicLogger.LOGGER.info("Cleared " + ThaumcraftApi.groupedObjectTags.size() + " unused Thaumcraft grouped aspects");
                    ThaumcraftApi.groupedObjectTags.clear();
                } catch (IncompatibleClassChangeError e) {
                    ArchaicLogger.LOGGER.info("Thaumcraft does not have a groupedObjectTags field");
                }
            }
        }
    }



    @EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        proxy.loadcomplete();
        printRecipeDebug();
        removeThaumcraftLeak();
    }

    @NetworkCheckHandler
    public boolean doVersionCheck(Map<String, String> mods, Side side) {
        /*
        if(mods.containsKey(Tags.MODID)) {
            String otherVersion = mods.get(Tags.MODID);
            if(!otherVersion.equals(Tags.VERSION)) {
                ArchaicLogger.LOGGER.error("Remote side " + side + " has different version " + otherVersion);
                return false;
            }
        }
        */
        return true;
    }

    public static boolean isArchaicConnection(NetHandlerPlayServer connection) {
        return FixHelper.unmoddedNetHandlers.contains(connection);
    }
}
