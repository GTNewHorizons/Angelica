package jss.notfine.util;

import net.minecraft.util.ResourceLocation;

public interface FontRendererExpansion {

    float[] getCharWidthf();

    void setCharWidthf(float[] widthf);

    ResourceLocation getDefaultFont();

    void setDefaultFont(ResourceLocation defaultFont);

    ResourceLocation getHDFont();

    void setHDFont(ResourceLocation hdFont);

    boolean getIsHD();

    void setIsHD(boolean isHD);

    float getFontAdj();

    void setFontAdj(float fontAdj);
}
