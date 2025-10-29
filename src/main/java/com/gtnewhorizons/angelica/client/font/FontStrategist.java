package com.gtnewhorizons.angelica.client.font;

import com.google.common.collect.HashMultiset;
import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.mixins.interfaces.ResourceAccessor;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultResourcePack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class FontStrategist {

    @Getter
    private static final Font[] availableFonts;
    public static final Logger LOGGER = LogManager.getLogger("Angelica");
    public static boolean customFontInUse;

    static {
        // get available fonts without duplicates (250 copies of dialog.plain need not apply)
        Font[] availableFontsDirty = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        HashMap<String, Font> fontSet = new HashMap<>();
        HashMultiset<String> duplicates = HashMultiset.create(); // for debugging

        for (Font font : availableFontsDirty) {
            String fontName = font.getFontName();
            if (fontSet.containsKey(fontName)) {
                duplicates.add(fontName);
            } else {
                fontSet.put(fontName, font);
            }
        }

        if (!duplicates.isEmpty()) {
            StringBuilder sb = new StringBuilder(duplicates.size() + " duplicate font(s) found in the list reported by Java: ");
            for (Iterator<String> iter = duplicates.stream().distinct().iterator(); iter.hasNext(); ) {
                String dupe = iter.next();
                sb.append(duplicates.count(dupe)).append("x ").append(dupe);
                if (iter.hasNext()) {
                    sb.append(", ");
                }
            }
            sb.append(". Some fonts may be missing from the font selection menu.");
            LOGGER.warn(sb.toString());
        }

        availableFonts = fontSet.values().stream().sorted(Comparator.comparing(Font::getFontName)).toArray(Font[]::new);

        // create and add the resource pack that provides fonts
        HashMap<String, File> packMap = new HashMap<>();
        for (int i = 0; i < FontProviderCustom.ATLAS_COUNT; i++) {
            packMap.put(FontProviderCustom.getPrimary().getAtlasResourceName(i), new File(FontProviderCustom.getPrimary().getAtlasFullPath(i)));
            packMap.put(FontProviderCustom.getFallback().getAtlasResourceName(i), new File(FontProviderCustom.getFallback().getAtlasFullPath(i)));
        }

        DefaultResourcePack fontResourcePack = new DefaultResourcePack(packMap);
        List defaultResourcePacks = ((ResourceAccessor) Minecraft.getMinecraft()).angelica$getDefaultResourcePacks();
        defaultResourcePacks.add(fontResourcePack);
        Minecraft.getMinecraft().refreshResources();

        // determine if a custom font is in use based on config values
        boolean primaryFontFound = false;
        boolean fallbackFontFound = false;
        for (Font availableFont : availableFonts) {
            if (Objects.equals(FontConfig.customFontNamePrimary, availableFont.getFontName())) {
                primaryFontFound = true;
                break;
            }
            if (Objects.equals(FontConfig.customFontNameFallback, availableFont.getFontName())) {
                fallbackFontFound = true;
                break;
            }
        }
        customFontInUse = (FontConfig.enableCustomFont && (primaryFontFound || fallbackFontFound));
    }

    /**
     Lets you get a FontProvider per char while respecting font priority and fallbacks, the unicode flag, whether or not
     SGA is on, if we're in a splash screen, if a font can even display a character in the first place, etc.
     */
    public static FontProvider getFontProvider(BatchingFontRenderer me, char chr, boolean customFontEnabled, boolean forceUnicode) {
        if (me.isSGA && FontProviderMC.get(true).isGlyphAvailable(chr)) {
            return FontProviderMC.get(true);
        }
        if (customFontEnabled && !me.isSplash) {
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
        FontProviderCustom.getPrimary().setFont(null);
        FontProviderCustom.getFallback().setFont(null);
        boolean primaryFontFound = false;
        boolean fallbackFontFound = false;
        for (int i = 0; i < availableFonts.length; i++) {
            if (Objects.equals(FontConfig.customFontNamePrimary, availableFonts[i].getFontName())) {
                FontProviderCustom.getPrimary().reloadFont(i);
                primaryFontFound = true;
            }
            if (Objects.equals(FontConfig.customFontNameFallback, availableFonts[i].getFontName())) {
                FontProviderCustom.getFallback().reloadFont(i);
                fallbackFontFound = true;
            }
            if (primaryFontFound && fallbackFontFound) { break; }
        }
        customFontInUse = (FontConfig.enableCustomFont && (primaryFontFound || fallbackFontFound));
    }
}
