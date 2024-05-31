package com.gtnewhorizons.angelica.utils;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import glowredman.txloader.Asset;
import glowredman.txloader.TXLoaderCore;

public class AssetLoader {

    // Texture paths from modern, starting with "minecraft/textures/"
    public static final String[] injectTexs = {
        "block/stone",
        "block/crafting_table_front",
        "block/crafting_table_side",
        "block/crafting_table_top",
        "block/oak_planks",
        "block/acacia_log_top",
        "block/acacia_log",
        "block/birch_log_top",
        "block/birch_log",
        "block/dark_oak_log_top",
        "block/dark_oak_log",
        "block/jungle_log_top",
        "block/jungle_log",
        "block/oak_log_top",
        "block/oak_log",
        "block/spruce_log_top",
        "block/spruce_log"
    };

    // Any equivalents from 7.10, starting with "minecraft/"
    // This can't be automatic because some of them are inconsistent
    public static final String[] oldInjectTexs = {
        "blocks/stone",
        "blocks/crafting_table_front",
        "blocks/crafting_table_side",
        "blocks/crafting_table_top",
        "blocks/planks_oak",
        "blocks/log_acacia_top",
        "blocks/log_acacia",
        "blocks/log_birch_top",
        "blocks/log_birch",
        "blocks/log_big_oak_top",
        "blocks/log_big_oak",
        "blocks/log_jungle_top",
        "blocks/log_jungle",
        "blocks/log_oak_top",
        "blocks/log_oak",
        "blocks/log_spruce_top",
        "blocks/log_spruce"
    };

    public static final String[] testTexs = {
        "block/lectern_base",
        "block/lectern_front",
        "block/lectern_base",
        "block/lectern_sides",
        "block/lectern_top",
        "block/oak_planks"
    };

    public static void load() {

        if (AngelicaConfig.injectQPRendering || AngelicaConfig.enableTestBlocks) {
            addModelAssets(
                "block/block",
                "block/cube",
                "block/cube_all",
                "block/cube_column"
            );
        }

        // For textures, we first clone old assets to their locations in modern, then copy any modern textures we don't
        // have equivalents for (only the lectern rn). Models are always downloaded, 7.10 doesn't have any.
        if (AngelicaConfig.injectQPRendering) {
            addModelAssets(
                "block/stone",
                "block/crafting_table",
                "block/acacia_log",
                "block/birch_log",
                "block/dark_oak_log",
                "block/jungle_log",
                "block/oak_log",
                "block/spruce_log");
            addTexAssets(oldInjectTexs, injectTexs, "1.7.10");
        }

        if (AngelicaConfig.enableTestBlocks) {
            addModelAssets("block/lectern");
            addTexAssets(testTexs, testTexs, "1.20.4");
        }
    }

    private static void addModelAssets(String... resourcePaths) {
        for (String path : resourcePaths) {
            TXLoaderCore.getAssetBuilder("minecraft/models/" + path + ".json")
                .setVersion("1.20.4")
                .setSource(Asset.Source.CLIENT)
                .add();
        }
    }

    private static void addTexAssets(String[] resourcePaths, String[] destPaths, String version) {
        for (int i = 0; i < resourcePaths.length; ++i) {
            TXLoaderCore.getAssetBuilder("minecraft/textures/" + resourcePaths[i] + ".png")
                .setOverride("minecraft/textures/blocks/" + destPaths[i] + ".png")
                .setVersion(version)
                .setSource(Asset.Source.CLIENT)
                .add();
        }
    }
}
