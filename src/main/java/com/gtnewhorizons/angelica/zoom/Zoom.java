package com.gtnewhorizons.angelica.zoom;

import lombok.Getter;
import net.minecraft.client.settings.KeyBinding;

public class Zoom {

    @Getter
    private static float zoom = 4.0F;
    @Getter
    private static final KeyBinding zoomKey = new KeyBinding("Zoom", 0, "key.categories.misc");

}
