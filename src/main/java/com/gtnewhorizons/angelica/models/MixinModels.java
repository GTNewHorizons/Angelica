package com.gtnewhorizons.angelica.models;

import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.models.json.JsonModel;
import com.gtnewhorizons.angelica.models.json.Loader;
import com.gtnewhorizons.angelica.models.json.Variant;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import net.minecraft.util.ResourceLocation;

import java.util.List;

public class MixinModels {

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
