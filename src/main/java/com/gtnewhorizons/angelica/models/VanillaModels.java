package com.gtnewhorizons.angelica.models;

import com.gtnewhorizon.gtnhlib.client.model.BakedModel;

public class VanillaModels {

    private static boolean init = false;
    public static BakedModel WORKBENCH;

    public static void init() {

        if (init) {
            throw new RuntimeException("Vanilla models were baked twice!");
        }

        init = true;
    }
}
