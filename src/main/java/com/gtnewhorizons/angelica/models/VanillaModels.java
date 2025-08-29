package com.gtnewhorizons.angelica.models;

import com.gtnewhorizon.gtnhlib.blockpos.IBlockPos;
import com.gtnewhorizon.gtnhlib.client.model.ModelLoader;
import com.gtnewhorizon.gtnhlib.client.model.Variant;
import com.gtnewhorizon.gtnhlib.client.model.json.ModelLocation;
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
