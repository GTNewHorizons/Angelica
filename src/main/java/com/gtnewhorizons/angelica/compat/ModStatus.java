package com.gtnewhorizons.angelica.compat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.falsepattern.endlessids.config.GeneralConfig;
import com.gtnewhorizons.angelica.compat.backhand.BackhandReflectionCompat;
import com.gtnewhorizons.angelica.helpers.LoadControllerHelper;
import com.gtnewhorizons.angelica.rendering.celeritas.CubeStatusEvents;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import mods.battlegear2.Battlegear;

public class ModStatus {
    public static final Logger LOGGER = LogManager.getLogger("ModCompat");

    public static boolean isBetterCrashesLoaded;
    public static boolean isNEIDLoaded;
    public static boolean isNEIDMetadataExtended;
    public static boolean isEIDLoaded;
    public static boolean isMetadataExtended;
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
    public static boolean isCosmeticArmorReworkedLoaded;
    public static boolean isFluidLoggedLoaded;
    public static boolean isCubicChunksLoaded;
    public static boolean isBOPLoaded;
    public static boolean isEtFuturumLoaded;

    public static void preInit() {
        if (Loader.isModLoaded("backhand")) {
            isBackhandLoaded = BackhandReflectionCompat.isBackhandLoaded();
        }

        isBetterCrashesLoaded = Loader.isModLoaded("bettercrashes");
        isNEIDLoaded = Loader.isModLoaded("neid");
        isEIDLoaded = Loader.isModLoaded("endlessids");
        isLotrLoaded = Loader.isModLoaded("lotr");
        isChunkAPILoaded = Loader.isModLoaded("chunkapi");
        isXaerosMinimapLoaded = Loader.isModLoaded("XaeroMinimap");
        isHoloInventoryLoaded = Loader.isModLoaded("holoinventory");
        isBattlegearLoaded = Loader.isModLoaded("battlegear2");
        isThaumcraftLoaded = Loader.isModLoaded("Thaumcraft");
        isThaumicHorizonsLoaded = Loader.isModLoaded("ThaumicHorizons");
        isBaublesLoaded = Loader.isModLoaded("Baubles");
        isCosmeticArmorReworkedLoaded = Loader.isModLoaded("cosmeticarmorreworked");
        isFluidLoggedLoaded = Loader.isModLoaded("fluidlogged");
        isCubicChunksLoaded = Loader.isModLoaded("cubicchunks");
        isEtFuturumLoaded = Loader.isModLoaded("etfuturum");

        isHoloInventoryLoaded = Loader.isModLoaded("holoinventory");
        isBOPLoaded = Loader.isModLoaded("BiomesOPlenty");

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

        if (isCubicChunksLoaded) {
            CubeStatusEvents.init();
        }

        boolean eidBlockItemExtended = false;
        boolean eidBiomeExtended = false;
        if (isEIDLoaded) {
            eidBlockItemExtended = GeneralConfig.extendBlockItem;
            eidBiomeExtended = GeneralConfig.extendBiome;
        }

        isEIDBiomeLoaded = eidBiomeExtended || Loader.isModLoaded("endlessids_biome");
        isMetadataExtended = isNEIDMetadataExtended || eidBlockItemExtended;
    }
}
