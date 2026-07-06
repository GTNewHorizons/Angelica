package jss.notfine;

import com.gtnewhorizons.angelica.Tags;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import jss.notfine.proxy.CommonProxy;
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

    @SidedProxy(clientSide = "jss.notfine.proxy.ClientProxy", serverSide = "jss.notfine.proxy.CommonProxy")
    public static CommonProxy proxy;

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

}
