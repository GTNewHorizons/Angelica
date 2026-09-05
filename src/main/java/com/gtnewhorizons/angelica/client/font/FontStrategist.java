package com.gtnewhorizons.angelica.client.font;

import com.google.common.collect.HashMultiset;
import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.mixins.interfaces.ResourceAccessor;
import cpw.mods.fml.client.SplashProgress;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;

public class FontStrategist {

    @Getter
    private static final Font[] availableFonts;
    public static final Logger LOGGER = LogManager.getLogger("Angelica");
    private static final boolean isSafeToUseAwtEnvironmentData = checkIsSafeToUseAwtEnvironmentData();

    static {
        HashMap<String, Font> fontSet = new HashMap<>();

        // get available fonts without duplicates (250 copies of dialog.plain need not apply)
        final Font[] availableFontsDirty;
        final HashMultiset<String> duplicates = HashMultiset.create(); // for debugging

        if (isSafeToUseAwtEnvironmentData) {
            availableFontsDirty = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        } else {
            LOGGER.warn("System font enumeration is disabled because AWT API is unsafe in this environment. Update lwjgl3ify!");
            availableFontsDirty = new Font[0];
        }

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

        LOGGER.info("Got {} fonts from GraphicsEnvironment ({} after deduplication)", availableFontsDirty.length, fontSet.size());

        loadBundledFonts(fontSet);

        availableFonts = fontSet.values().stream().sorted(Comparator.comparing(Font::getFontName)).toArray(Font[]::new);

        // create and add the resource pack that provides fonts
        HashMap<String, File> packMap = new HashMap<>();
        for (int i = 0; i < FontProviderCustom.ATLAS_COUNT; i++) {
            packMap.put(FontProviderCustom.getPrimary().getAtlasResourceName(i), new File(FontProviderCustom.getPrimary().getAtlasFullPath(i)));
            packMap.put(FontProviderCustom.getFallback().getAtlasResourceName(i), new File(FontProviderCustom.getFallback().getAtlasFullPath(i)));
        }

        Minecraft mc = Minecraft.getMinecraft();
        DefaultResourcePack fontResourcePack = new DefaultResourcePack(packMap);

        ((ResourceAccessor) mc).angelica$getDefaultResourcePacks().add(fontResourcePack);
        ((SimpleReloadableResourceManager) mc.getResourceManager()).reloadResourcePack(fontResourcePack);
    }

    private static boolean checkIsSafeToUseAwtEnvironmentData() {
        if (!GraphicsEnvironment.isHeadless()) return true;

        try (InputStream stream = ClassLoader.getSystemResourceAsStream(
            "META-INF/lwjgl3ify-forgePatches-version.txt")) {

            if (stream == null) {
                LOGGER.warn("Failed to identify lwjgl3ify version");
                return false;
            }

            String version;
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                version = reader.readLine();
            }

            // See https://github.com/GTNewHorizons/RetroFuturaBootstrap/commit/6d39c56ba0f1496b5599a3660d1578df3b28ff17
            // This change corresponds to RFB 1.0.14, used since lwjgl3ify 3.0.8
            return new DefaultArtifactVersion(version)
                .compareTo(new DefaultArtifactVersion("3.0.8")) >= 0;
        } catch (IOException e) {
            LOGGER.warn("Failed to identify lwjgl3ify version", e);
            return false;
        }
    }

    // Load .ttf/.otf shipped with the pack so customFontName* can point at a font that isn't installed
    // on the system. fontfiles/ is SmoothFont's folder, kept so packs built for it work unchanged.
    private static void loadBundledFonts(HashMap<String, Font> fontSet) {
        File gameDir = Minecraft.getMinecraft().mcDataDir;
        File[] fontDirs = { new File(gameDir, "fontfiles"), new File(gameDir, "config/angelica/fonts") };
        GraphicsEnvironment ge = !isSafeToUseAwtEnvironmentData ? null : GraphicsEnvironment.getLocalGraphicsEnvironment();
        int loaded = 0;
        for (File dir : fontDirs) {
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".ttf") || name.toLowerCase().endsWith(".otf"));
            if (files == null) continue;
            for (File file : files) {
                try {
                    Font font = Font.createFont(Font.TRUETYPE_FONT, file);
                    String name = font.getFontName();
                    if (fontSet.containsKey(name)) {
                        String deduped = name;
                        int n = 1;
                        while (fontSet.containsKey(deduped)) deduped = name + "_" + n++;
                        LOGGER.warn("Font name {} (from {}) is already registered; registering this one as {}", name, file.getName(), deduped);
                        name = deduped;
                    }
                    if (ge != null) ge.registerFont(font);
                    fontSet.put(name, font);
                    loaded++;
                    LOGGER.info("Loaded font {} from {}/{}", name, dir.getName(), file.getName());
                } catch (FontFormatException | IOException e) {
                    LOGGER.error("Couldn't load font {}", file.getPath(), e);
                }
            }
        }
        if (loaded > 0) LOGGER.info("Loaded {} bundled font(s) from the pack", loaded);
    }

    /**
     Lets you get a FontProvider per char while respecting font priority and fallbacks, the unicode flag, whether
     SGA is on, if we're in a splash screen, if a font can even display a character in the first place, etc.
     */
    public static FontProvider getFontProvider(BatchingFontRenderer me, char chr, boolean customFontEnabled, boolean forceUnicode) {
        if (me.isSGA && FontProviderMC.get(true).isGlyphAvailable(chr)) {
            return FontProviderMC.get(true);
        }
        if (me.bookMode) {
            return FontProviderUnicode.get();
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
        for (int i = 0; i < availableFonts.length; i++) {
            if (Objects.equals(FontConfig.customFontNamePrimary, availableFonts[i].getFontName())) {
                FontProviderCustom.getPrimary().reloadFont(i);
            }
            if (Objects.equals(FontConfig.customFontNameFallback, availableFonts[i].getFontName())) {
                FontProviderCustom.getFallback().reloadFont(i);
            }
        }
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
