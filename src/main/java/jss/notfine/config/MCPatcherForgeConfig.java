package jss.notfine.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import java.util.logging.Level;

public class MCPatcherForgeConfig {

    @Config(modid = "mcpatcherforge", category = "custom_colors")
    public static class CustomColors {
        @Config.Comment("Enable the custom colors module")
        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean enabled;

        @Config.Comment("logging level")
        @Config.DefaultEnum("INFO")
        @Config.RequiresMcRestart
        public static LogLevel logging;

        @Config.DefaultInt(0)
        @Config.RequiresMcRestart
        public static int yVariance;

        @Config.DefaultInt(4)
        @Config.RequiresMcRestart
        public static int blockBlendRadius;

        @Config.DefaultInt(7)
        @Config.RequiresMcRestart
        public static int fogBlendRadius;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean swampColors;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean water;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean tree;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean redstone;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean stem;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean otherBlocks;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean smoothBiomes;

        @Config.DefaultBoolean(false)
        @Config.RequiresMcRestart
        public static boolean testColorSmoothing;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean potion;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean particle;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean fog;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean clouds;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean map;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean dye;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean text;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean xporb;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean egg;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean lightmaps;
    }

    @Config(modid = "mcpatcherforge", category = "custom_item_textures")
    public static class CustomItemTextures {
        @Config.Comment("Enable the custom item textures module")
        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean enabled;

        @Config.Comment("logging level")
        @Config.DefaultEnum("INFO")
        @Config.RequiresMcRestart
        public static LogLevel logging;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean items;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean enchantments;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean armor;
    }

    @Config(modid = "mcpatcherforge", category = "connected_textures")
    public static class ConnectedTextures {
        @Config.Comment("Enable the connected textures module")
        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean enabled;

        @Config.Comment("logging level")
        @Config.DefaultEnum("INFO")
        @Config.RequiresMcRestart
        public static LogLevel logging;

        @Config.DefaultInt(4)
        public static int maxRecursion;

        @Config.DefaultBoolean(false)
        public static boolean debugTextures;

        @Config.DefaultBoolean(false)
        public static boolean betterGrass;

        @Config.DefaultBoolean(true)
        public static boolean standard;

        @Config.DefaultBoolean(true)
        public static boolean nonStandard;

        @Config.DefaultBoolean(false)
        public static boolean glassPane;
    }

    @Config(modid = "mcpatcherforge", category = "extended_hd")
    public static class ExtendedHD {
        @Config.Comment("Enable the extended hd module")
        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean enabled;

        @Config.Comment("logging level")
        @Config.DefaultEnum("INFO")
        @Config.RequiresMcRestart
        public static LogLevel logging;

        @Config.DefaultInt(3)
        @Config.RequiresMcRestart
        public static int maxMipMapLevel;

        @Config.DefaultInt(1)
        @Config.RequiresMcRestart
        public static int anisotropicFiltering;

        @Config.DefaultInt(0)
        @Config.RequiresMcRestart
        @Config.Name("lod bias")
        public static int lodBias;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean animations;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean fancyCompass;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean fancyClock;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean useGL13;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean useScratchTexture;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        @Config.Name("HDFont")
        public static boolean hdFont;

        @Config.DefaultBoolean(false)
        @Config.RequiresMcRestart
        public static boolean nonHDFontWidth;

        @Config.DefaultBoolean(false)
        @Config.RequiresMcRestart
        public static boolean mipmap;
    }

    @Config(modid = "mcpatcherforge", category = "random_mobs")
    public static class RandomMobs {
        @Config.Comment("Enable the random mobs module")
        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean enabled;

        @Config.Comment("logging level")
        @Config.DefaultEnum("INFO")
        @Config.RequiresMcRestart
        public static LogLevel logging;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean leashLine;
    }

    @Config(modid = "mcpatcherforge", category = "better_skies")
    public static class BetterSkies {
        @Config.Comment("Enable the better skies module")
        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean enabled;

        @Config.Comment("logging level")
        @Config.DefaultEnum("INFO")
        @Config.RequiresMcRestart
        public static LogLevel logging;

        @Config.DefaultInt(16)
        @Config.RequiresMcRestart
        public static int horizon;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean brightenFireworks;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean skybox;

        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean unloadTextures;
    }

    public static void registerConfig() throws ConfigException {
        ConfigurationManager.registerConfig(CustomColors.class);
        ConfigurationManager.registerConfig(CustomItemTextures.class);
        ConfigurationManager.registerConfig(ConnectedTextures.class);
        ConfigurationManager.registerConfig(ExtendedHD.class);
        ConfigurationManager.registerConfig(RandomMobs.class);
        ConfigurationManager.registerConfig(BetterSkies.class);
    }

    public enum LogLevel {
        OFF(Level.OFF),
        SEVERE(Level.SEVERE),
        WARNING(Level.WARNING),
        INFO(Level.INFO),
        CONFIG(Level.CONFIG),
        FINE(Level.FINE),
        FINER(Level.FINER),
        FINEST(Level.FINEST),
        ALL(Level.ALL);

        @Config.Ignore
        public final Level level;

        LogLevel(Level level) {
            this.level = level;
        }
    }
}
