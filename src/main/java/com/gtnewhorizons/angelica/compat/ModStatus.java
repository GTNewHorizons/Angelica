package com.gtnewhorizons.angelica.compat;

import com.gtnewhorizons.angelica.helpers.LoadControllerHelper;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import net.dries007.holoInventory.HoloInventory;

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

    public static void preInit(){
        isBetterCrashesLoaded = Loader.isModLoaded("bettercrashes");
        isNEIDLoaded = Loader.isModLoaded("neid");
        isOldNEIDLoaded = Loader.isModLoaded("notenoughIDs");
        isLotrLoaded = Loader.isModLoaded("lotr");
        isChunkAPILoaded = Loader.isModLoaded("chunkapi");
        isEIDBiomeLoaded = Loader.isModLoaded("endlessids_biome");
        isXaerosMinimapLoaded = Loader.isModLoaded("XaeroMinimap");
        isHoloInventoryLoaded = Loader.isModLoaded("holoinventory");

        if (isHoloInventoryLoaded){
            isHoloInventoryLoaded = new DefaultArtifactVersion("2.4.4-GTNH")
                .compareTo(
                    LoadControllerHelper.getOwningMod(HoloInventory.class).getProcessedVersion()
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
