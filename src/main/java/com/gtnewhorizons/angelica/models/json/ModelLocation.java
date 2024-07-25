package com.gtnewhorizons.angelica.models.json;

import net.minecraft.util.ResourceLocation;

public class ModelLocation extends ResourceLocation {

    /**
     * Create a model location in the minecraft domain.
     */
    public ModelLocation(String path) {
        super("models/" + path + ".json");
    }

    /**
     * Create a model location in any domain
     */
    public ModelLocation(String domain, String path) {
        super(domain, "models/" + path + ".json");
    }
}
