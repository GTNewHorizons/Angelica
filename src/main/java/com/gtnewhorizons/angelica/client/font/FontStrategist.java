package com.gtnewhorizons.angelica.client.font;

import com.google.common.collect.HashMultiset;
import com.gtnewhorizons.angelica.config.FontConfig;
import cpw.mods.fml.client.SplashProgress;
import lombok.Getter;
import net.minecraft.client.gui.FontRenderer;

import java.awt.*;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;

import static com.gtnewhorizons.angelica.AngelicaMod.LOGGER;
import static com.gtnewhorizons.angelica.client.font.BatchingFontRenderer.initializeCustomFonts;
import static com.gtnewhorizons.angelica.client.font.BatchingFontRenderer.primaryTextureArray;

public final class FontStrategist {

    @Getter
    private static final Font[] availableFonts;

    public static Font primaryFont;
    public static Font secondaryFont;

    public static final int ATLAS_CHARS = 256;

    static {
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.warn("GraphicsEnvironment.isHeadless() returned true! Custom fonts will be unavailable. This is likely a MacOS issue.");
            availableFonts = new Font[0];
        } else {
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

            LOGGER.info("Got {} fonts from GraphicsEnvironment ({} after deduplication)", availableFontsDirty.length, availableFonts.length);
            reloadCustomFontProviders();
        }
    }

    public static Font getFont(int index) {
        return availableFonts[index];
    }


    public static void reloadCustomFontProviders() {
        primaryFont = null;
        secondaryFont = null;
        for (int i = 0; i < availableFonts.length; i++) {
            final Font font = getFont(i);
            final String fontName = font.getFontName();
            if (FontConfig.customFontNamePrimary.equals(fontName)) {
                primaryFont = font.deriveFont((float) FontConfig.customFontQuality);
            }
//            if (FontConfig.customFontNameFallback.equals(fontName)) {
//                secondaryFont = font.deriveFont((float) FontConfig.customFontQuality);
//
//                if (secondaryTextureArray != null) {
//                    secondaryTextureArray.delete();
//                    secondaryTextureArray = null;
//                }
//            }
        }
        if (primaryTextureArray != null) {
            primaryTextureArray.delete();
            primaryTextureArray = null;
        }
        initializeCustomFonts();
    }

    public static boolean isSplashFontRendererActive(FontRenderer fontRenderer) {
        // noinspection deprecation
        boolean active = fontRenderer instanceof SplashProgress.SplashFontRenderer;

        try {
            Class<?> customSplashClass = Class.forName("gkappa.modernsplash.CustomSplash$SplashFontRenderer");
            active = active || customSplashClass.isInstance(fontRenderer);
        } catch (ClassNotFoundException ignored) {
        }

        return active;
    }
}
