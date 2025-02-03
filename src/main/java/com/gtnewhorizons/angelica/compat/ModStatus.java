package com.gtnewhorizons.angelica.compat;

import com.gtnewhorizons.angelica.helpers.LoadControllerHelper;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import mods.battlegear2.Battlegear;
import net.dries007.holoInventory.HoloInventory;
import xonin.backhand.Backhand;

public class ModStatus {
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

    public static void preInit(){
        isBetterCrashesLoaded = Loader.isModLoaded("bettercrashes");
        isNEIDLoaded = Loader.isModLoaded("neid");
        isOldNEIDLoaded = Loader.isModLoaded("notenoughIDs");
        isLotrLoaded = Loader.isModLoaded("lotr");
        isChunkAPILoaded = Loader.isModLoaded("chunkapi");
        isEIDBiomeLoaded = Loader.isModLoaded("endlessids_biome");
        isXaerosMinimapLoaded = Loader.isModLoaded("XaeroMinimap");
        isHoloInventoryLoaded = Loader.isModLoaded("holoinventory");
        isBattlegearLoaded = Loader.isModLoaded("battlegear2");
        isBackhandLoaded = Loader.isModLoaded("backhand");
        isThaumcraftLoaded = Loader.isModLoaded("Thaumcraft");
        isThaumicHorizonsLoaded = Loader.isModLoaded("ThaumicHorizons");
        isBaublesLoaded = Loader.isModLoaded("Baubles");

        if (isHoloInventoryLoaded){
            isHoloInventoryLoaded = new DefaultArtifactVersion("2.4.4-GTNH")
                .compareTo(
                    LoadControllerHelper.getOwningMod(HoloInventory.class).getProcessedVersion()
                ) <= 0;
        }

        // remove compat with original release of BG2
        if (isBattlegearLoaded){
            isBattlegearLoaded = new DefaultArtifactVersion("1.2.0")
                .compareTo(
                    LoadControllerHelper.getOwningMod(Battlegear.class).getProcessedVersion()
                ) <= 0;
        }

        if (isBackhandLoaded){
            isBackhandLoaded = new DefaultArtifactVersion("1.6.9")
                .compareTo(
                    LoadControllerHelper.getOwningMod(Backhand.class).getProcessedVersion()
                ) <= 0;
        }


        isNEIDMetadataExtended = false;
        if (isNEIDLoaded) {
            int majorVersion = Integer.parseInt(Loader.instance().getIndexedModList().get("neid").getVersion().split("\\.")[0]);
            if (majorVersion >= 2) {
                isNEIDMetadataExtended = true;
            }
        }
    }
}
