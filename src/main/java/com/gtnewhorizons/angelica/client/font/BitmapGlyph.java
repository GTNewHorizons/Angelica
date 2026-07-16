package com.gtnewhorizons.angelica.client.font;

import net.minecraft.util.ResourceLocation;

public record BitmapGlyph(ResourceLocation texture, float uStart, float vStart, float uSize, float vSize, float glyphW,
                          float advance) {

}
