package com.gtnewhorizons.angelica.zoom;

import lombok.Getter;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MathHelper;

public class Zoom {

    @Getter
    private static float zoom = 4.0F;
    @Getter
    private static final KeyBinding zoomKey = new KeyBinding("Zoom", 0, "key.categories.misc");

    public static void modifyZoom(int eventDWheel) {
        if (eventDWheel == 0) return;
        zoom = MathHelper.clamp_float(zoom + Integer.signum(eventDWheel), 1.0F, 16.0F);
    }
}
