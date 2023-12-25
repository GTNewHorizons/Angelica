package com.gtnewhorizons.angelica.mixins.interfaces;

import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import net.minecraft.util.ResourceLocation;

public interface FontRendererAccessor {
    BatchingFontRenderer angelica$getBatcher();

    void angelica$bindTexture(ResourceLocation location);
}
