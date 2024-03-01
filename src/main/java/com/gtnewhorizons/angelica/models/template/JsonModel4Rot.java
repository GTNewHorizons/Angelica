package com.gtnewhorizons.angelica.models.template;

import com.gtnewhorizons.angelica.api.QuadView;
import com.gtnewhorizons.angelica.models.json.JsonModel;
import com.gtnewhorizons.angelica.models.json.Loader;
import com.gtnewhorizons.angelica.models.json.Variant;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import lombok.Getter;
import net.minecraft.util.ResourceLocation;

import java.util.List;

/**
 * Use this to create JSON model rotated in 4 directions
 */
public class JsonModel4Rot {

    @Getter
    private final JsonModel[] models = new JsonModel[4];
    private final Variant[] modelIds;
    private static final List<QuadView> EMPTY = ObjectImmutableList.of();

    public JsonModel4Rot(ResourceLocation modelLoc) {

        this.modelIds = new Variant[] {
            new Variant(modelLoc, 0,   0, false),
            new Variant(modelLoc, 180, 0, false),
            new Variant(modelLoc, 90,  0, false),
            new Variant(modelLoc, 270, 0, false)
        };

        Loader.registerModels(() -> loadModels(this), this.modelIds);
    }

    public static void loadModels(JsonModel4Rot model) {
        for (int i = 0; i < 4; ++i)
            model.models[i] = Loader.getModel(model.modelIds[i]);
    }
}
