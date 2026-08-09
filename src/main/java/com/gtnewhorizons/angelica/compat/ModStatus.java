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
    public static boolean isSimpleSkinBackportLoaded;

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
        isThaumcraftLoaded = Loader.isModLoaded("Thaumcraft");
        isThaumicHorizonsLoaded = Loader.isModLoaded("ThaumicHorizons");
        isBaublesLoaded = Loader.isModLoaded("Baubles");
        isCosmeticArmorReworkedLoaded = Loader.isModLoaded("cosmeticarmorreworked");
        isFluidLoggedLoaded = Loader.isModLoaded("fluidlogged");
        isCubicChunksLoaded = Loader.isModLoaded("cubicchunks");
        isEtFuturumLoaded = Loader.isModLoaded("etfuturum");
        isSimpleSkinBackportLoaded = Loader.isModLoaded("simpleskinbackport");

        isHoloInventoryLoaded = Loader.isModLoaded("holoinventory");
        isBOPLoaded = Loader.isModLoaded("BiomesOPlenty");

        // Angelica's compat relies on GTNH extensions, so this should only be true for NH Battlegear2, 1.2.0+
        if (Loader.isModLoaded("battlegear2")) {
            // Don't ask me why battlegear2 reports as 1.7.10, I don't know
            final var OG_BG2_VER = new DefaultArtifactVersion("1.7.10");
            final var NH_BG2_VER = new DefaultArtifactVersion("1.2.0");

            @SuppressWarnings("DataFlowIssue")
            var battlegearVersion = LoadControllerHelper.getOwningMod(Battlegear.class).getProcessedVersion();
            // We can be sure the NH version will never be 1.7.10, because that garbage got archived.
            isBattlegearLoaded = battlegearVersion.compareTo(OG_BG2_VER) != 0 && battlegearVersion.compareTo(NH_BG2_VER) >= 0;
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
