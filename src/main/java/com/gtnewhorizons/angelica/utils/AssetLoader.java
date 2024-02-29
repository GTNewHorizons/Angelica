package com.gtnewhorizons.angelica.utils;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import glowredman.txloader.Asset;
import glowredman.txloader.TXLoaderCore;

public class AssetLoader {

    public static void load() {

        if (AngelicaConfig.injectQPRendering) {
            addAssets(
                "models/block/block.json",
                "models/block/cube.json",
                "models/block/cube_all.json",
                "models/block/lectern.json",
                "models/block/stone.json"
            );
            addTexAssets(
                "block/lectern_base.png",
                "block/lectern_front.png",
                "block/lectern_base.png",
                "block/lectern_sides.png",
                "block/lectern_top.png",
                "block/oak_planks.png",
                "block/stone.png");
        }
    }

    private static void addAssets(String... resourcePaths) {
        for (String path : resourcePaths) {
            TXLoaderCore.getAssetBuilder("minecraft/" + path)
                .setVersion("1.20.4")
                .setSource(Asset.Source.CLIENT)
                .add();
        }
    }

    private static void addTexAssets(String... resourcePaths) {
        for (String path : resourcePaths) {
            TXLoaderCore.getAssetBuilder("minecraft/textures/" + path)
                .setOverride("minecraft/textures/blocks/" + path)
                .setVersion("1.20.4")
                .setSource(Asset.Source.CLIENT)
                .add();
        }
    }
}
