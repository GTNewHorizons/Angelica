package com.gtnewhorizons.angelica.models.template;

import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.api.ModelLoader;
import com.gtnewhorizons.angelica.api.Variant;
import net.minecraft.util.ResourceLocation;

/**
 * Use this to create JSON model rotatable in 4 directions - NSWE
 */
public class Model4Rot {

    public final QuadProvider[] models = new QuadProvider[4];
    private final Variant[] modelIds;

    public Model4Rot(ResourceLocation modelLoc) {

        this.modelIds = new Variant[] {
            new Variant(modelLoc, 0,   0, false),
            new Variant(modelLoc, 180, 0, false),
            new Variant(modelLoc, 90,  0, false),
            new Variant(modelLoc, 270, 0, false)
        };

        ModelLoader.registerModels(() -> loadModels(this), this.modelIds);
    }

    public static void loadModels(Model4Rot model) {
        for (int i = 0; i < 4; ++i)
            model.models[i] = ModelLoader.getModel(model.modelIds[i]);
    }
}
