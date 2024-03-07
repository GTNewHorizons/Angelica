package com.gtnewhorizons.angelica.utils;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import glowredman.txloader.Asset;
import glowredman.txloader.TXLoaderCore;

public class AssetLoader {

    public static final String[] injectTexs = {
        "block/stone",
        "block/crafting_table_front",
        "block/crafting_table_side",
        "block/crafting_table_top",
        "block/oak_planks"
    };

    // This can't be automatic because some of them are inconsistent
    public static final String[] oldInjectTexs = {
        "blocks/stone",
        "blocks/crafting_table_front",
        "blocks/crafting_table_side",
        "blocks/crafting_table_top",
        "blocks/planks_oak"
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
                "block/cube_all"
            );
        }

        // This ordering is intentional. injectQPRendering prioritizes 7.10 textures, so it should run first to prevent
        // modern textures from loading.
        if (AngelicaConfig.injectQPRendering) {
            addModelAssets(
                "block/stone",
                "block/crafting_table");
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
