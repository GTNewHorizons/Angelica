package com.gtnewhorizons.angelica;

import static com.gtnewhorizons.angelica.AngelicaMod.MOD_ID;

import com.gtnewhorizons.angelica.common.BlockError;
import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.proxy.CommonProxy;
import com.gtnewhorizons.angelica.utils.AnimationMode;
import com.gtnewhorizons.angelica.utils.ManagedEnum;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
        modid = MOD_ID,
        name = "Angelica",
        version = Tags.VERSION,
        dependencies = """
            required-after:gtnhlib@[0.6.6,);\
            before:lwjgl3ify@[1.5.3,);\
            after:hodgepodge@[2.4.4,);\
            after:CodeChickenCore@[1.2.0,);\
            after:archaicfix@[0.7.0,);\
            """,
        acceptedMinecraftVersions = "[1.7.10]",
        acceptableRemoteVersions = "*",
        guiFactory = "com.gtnewhorizons.angelica.config.AngelicaGuiConfigFactory")
public class AngelicaMod {
    public static final String MOD_ID = "angelica";

    @SidedProxy(clientSide = "com.gtnewhorizons.angelica.proxy.ClientProxy", serverSide = "com.gtnewhorizons.angelica.proxy.CommonProxy")
    public static CommonProxy proxy;

    public static final boolean lwjglDebug = Boolean.parseBoolean(System.getProperty("org.lwjgl.util.Debug", "false"));

    public static final ManagedEnum<AnimationMode> animationsMode = new ManagedEnum<>(AnimationMode.VISIBLE_ONLY);

    public static BlockError blockError;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModStatus.preInit();
        proxy.preInit(event);
        blockError = new BlockError();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }
}
