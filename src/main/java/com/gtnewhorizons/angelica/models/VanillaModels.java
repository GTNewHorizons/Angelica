package com.gtnewhorizons.angelica.models;

import com.gtnewhorizons.angelica.models.json.JsonModel;
import com.gtnewhorizons.angelica.models.json.Loader;
import com.gtnewhorizons.angelica.models.json.Variant;
import net.minecraft.util.ResourceLocation;

public class VanillaModels {

    public static final Variant stoneVariant = new Variant(
        new ResourceLocation("blocks/stone"),
        0,
        0,
        true
    );
    public static JsonModel stoneModel;

    public static void loadModels() {

        stoneModel = Loader.getModel(stoneVariant);
    }
}
