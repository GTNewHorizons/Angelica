package com.gtnewhorizons.angelica.client.font;

import net.minecraft.util.ResourceLocation;

public interface SupplementaryGlyphSource extends FontProvider {

    float getUStartCp(int cp);
    float getVStartCp(int cp);
    float getXAdvanceCp(int cp);
    float getGlyphWCp(int cp);
    float getUSizeCp(int cp);
    float getVSizeCp(int cp);
    ResourceLocation getTextureCp(int cp);

    boolean isUnifont();
}
