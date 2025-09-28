package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.mixins.interfaces.ResourceAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultResourcePack;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class FontStrategist {

    static {
        HashMap<String, File> packMap = new HashMap<>();
        for (int i = 0; i < FontProviderCustom.ATLAS_COUNT; i++) {
            packMap.put(FontProviderCustom.getPrimary().getAtlasResourceName(i), new File(FontProviderCustom.getPrimary().getAtlasFullPath(i)));
            packMap.put(FontProviderCustom.getFallback().getAtlasResourceName(i), new File(FontProviderCustom.getFallback().getAtlasFullPath(i)));
        }

        DefaultResourcePack fontResourcePack = new DefaultResourcePack(packMap);
        List defaultResourcePacks = ((ResourceAccessor) Minecraft.getMinecraft()).angelica$getDefaultResourcePacks();
        defaultResourcePacks.add(fontResourcePack);
        Minecraft.getMinecraft().refreshResources();
    }

    /**
     Lets you get a FontProvider per char while respecting font priority and fallbacks, the unicode flag, whether or not
     SGA is on, if a font can even display a character in the first place, etc.
     */
    public static FontProvider getFontProvider(char chr, boolean isSGA, boolean customFontEnabled, boolean forceUnicode) {
        if (isSGA && FontProviderMC.get(true).isGlyphAvailable(chr)) {
            return FontProviderMC.get(true);
        }
        if (customFontEnabled) {
            FontProvider fp;
            fp = FontProviderCustom.getPrimary();
            if (fp.isGlyphAvailable(chr)) { return fp; }
            fp = FontProviderCustom.getFallback();
            if (fp.isGlyphAvailable(chr)) { return fp; }
            return FontProviderUnicode.get();
        } else {
            if (!forceUnicode && FontProviderMC.get(false).isGlyphAvailable(chr)) {
                return FontProviderMC.get(false);
            } else {
                return FontProviderUnicode.get();
            }
        }
    }

    public static void reloadCustomFontProviders() {
        Font[] availableFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        FontProviderCustom.getPrimary().setFont(null);
        FontProviderCustom.getFallback().setFont(null);
        for (int i = 0; i < availableFonts.length; i++) {
            if (Objects.equals(FontConfig.customFontNamePrimary, availableFonts[i].getFontName())) {
                FontProviderCustom.getPrimary().reloadFont(i);
            }
            if (Objects.equals(FontConfig.customFontNameFallback, availableFonts[i].getFontName())) {
                FontProviderCustom.getFallback().reloadFont(i);
            }
        }
    }
}
