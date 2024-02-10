package com.gtnewhorizons.angelica.compat;

import cpw.mods.fml.common.Loader;

public class ModStatus {
    /**
     * Mixin Version
     */
    public static boolean isNEIDLoaded;
    /**
     * ASM Version
     */
    public static boolean isOldNEIDLoaded;

    public static boolean isNEIDMetadataExtended;
    public static boolean isLotrLoaded;
    public static boolean isChunkAPILoaded;
    public static boolean isEIDBiomeLoaded;

    public static void preInit(){
        isNEIDLoaded = Loader.isModLoaded("neid");
        isOldNEIDLoaded = Loader.isModLoaded("notenoughIDs");
        isLotrLoaded = Loader.isModLoaded("lotr");
        isChunkAPILoaded = Loader.isModLoaded("chunkapi");
        isEIDBiomeLoaded = Loader.isModLoaded("endlessids_biome");

        isNEIDMetadataExtended = false;
        if (isNEIDLoaded) {
            int majorVersion = Integer.parseInt(Loader.instance().getIndexedModList().get("neid").getVersion().split("\\.")[0]);
            if (majorVersion >= 2) {
                isNEIDMetadataExtended = true;
            }
        }
    }
}
