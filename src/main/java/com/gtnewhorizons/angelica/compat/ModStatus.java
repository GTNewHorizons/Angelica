package com.gtnewhorizons.angelica.compat;

import com.gtnewhorizons.angelica.compat.backhand.BackhandReflectionCompat;
import com.gtnewhorizons.angelica.compat.hextext.HexTextServices;
import com.gtnewhorizons.angelica.helpers.LoadControllerHelper;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import mods.battlegear2.Battlegear;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ModStatus {
    public static final Logger LOGGER = LogManager.getLogger("ModCompat");

    public static BackhandReflectionCompat backhandCompat;
    /**
     * Mixin Version
     */
    public static boolean isNEIDLoaded;
    /**
     * ASM Version
     */
    public static boolean isOldNEIDLoaded;
    public static boolean isBetterCrashesLoaded;
    public static boolean isNEIDMetadataExtended;
    public static boolean isLotrLoaded;
    public static boolean isChunkAPILoaded;
    public static boolean isEIDBiomeLoaded;
    public static boolean isXaerosMinimapLoaded;
    public static boolean isHoloInventoryLoaded;
    public static boolean isBattlegearLoaded;
    public static boolean isBackhandLoaded;
    public static boolean isThaumcraftLoaded;
    public static boolean isThaumicHorizonsLoaded;
    public static boolean isBaublesLoaded;
    public static boolean isFluidLoggedLoaded;
    public static boolean isHexTextLoaded;

    public static void preInit(){
        isBackhandLoaded = Loader.isModLoaded("backhand");
        if(isBackhandLoaded) {
            isBackhandLoaded = BackhandReflectionCompat.isBackhandLoaded();
        }

        isBetterCrashesLoaded = Loader.isModLoaded("bettercrashes");
        isNEIDLoaded = Loader.isModLoaded("neid");
        isOldNEIDLoaded = Loader.isModLoaded("notenoughIDs");
        isLotrLoaded = Loader.isModLoaded("lotr");
        isChunkAPILoaded = Loader.isModLoaded("chunkapi");
        isEIDBiomeLoaded = Loader.isModLoaded("endlessids_biome");
        isXaerosMinimapLoaded = Loader.isModLoaded("XaeroMinimap");
        isHoloInventoryLoaded = Loader.isModLoaded("holoinventory");
        isBattlegearLoaded = Loader.isModLoaded("battlegear2");
        isThaumcraftLoaded = Loader.isModLoaded("Thaumcraft");
        isThaumicHorizonsLoaded = Loader.isModLoaded("ThaumicHorizons");
        isBaublesLoaded = Loader.isModLoaded("Baubles");
        isFluidLoggedLoaded = Loader.isModLoaded("fluidlogged");
        isHexTextLoaded = Loader.isModLoaded("hextext");
        HexTextServices.reportDiagnostics();

        isHoloInventoryLoaded = Loader.isModLoaded("holoinventory");

        // remove compat with original release of BG2
        if (isBattlegearLoaded){
            isBattlegearLoaded = new DefaultArtifactVersion("1.2.0")
                .compareTo(
                    LoadControllerHelper.getOwningMod(Battlegear.class).getProcessedVersion()
                ) <= 0;
        }

        isNEIDMetadataExtended = false;
        if (isNEIDLoaded) {
            final int majorVersion = Integer.parseInt(Loader.instance().getIndexedModList().get("neid").getVersion().split("\\.")[0]);
            if (majorVersion >= 2) {
                isNEIDMetadataExtended = true;
            }
        }
    }
}
