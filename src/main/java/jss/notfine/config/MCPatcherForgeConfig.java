package jss.notfine.config;

import java.io.File;
import java.util.logging.Level;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;

// Adapted from hodgepodge
public class MCPatcherForgeConfig {

    private static MCPatcherForgeConfig config;

    // CUSTOM_COLORS
    public boolean customColorsEnabled;
    public String customColorsLoggingLevel;
    public int yVariance;
    public int blockBlendRadius;
    public int fogBlendRadius;
    public boolean swampColors;
    public boolean ccWater;
    public boolean ccTree;
    public boolean ccRedstone;
    public boolean ccStem;
    public boolean ccOtherBlocks;
    public boolean smoothBiomes;
    public boolean testColorSmoothing;
    public boolean ccPotion;
    public boolean ccParticle;
    public boolean ccFog;
    public boolean ccClouds;
    public boolean ccMap;
    public boolean ccDye;
    public boolean ccText;
    public boolean ccXPOrb;
    public boolean ccEgg;
    public boolean ccLightmaps;

    // CUSTOM_ITEM_TEXTURES
    public boolean customItemTexturesEnabled;
    public String customItemTexturesLoggingLevel;
    public boolean citItems;
    public boolean citEnchantments;
    public boolean citArmor;

    // CONNECTED_TEXTURES
    public boolean connectedTexturesEnabled;
    public String connectedTexturesLoggingLevel;
    public int maxRecursion;
    public boolean debugTextures;
    public boolean betterGrass;
    public boolean ctmStandard;
    public boolean ctmNonStandard;
    public boolean ctmGlassPane;

    // EXTENDED_HD
    public boolean extendedHDEnabled;
    public String extendedHDLoggingLevel;
    public int maxMipMapLevel;
    public int anisotropicFiltering;
    public int lodBias;
    public boolean animations;
    public boolean fancyCompass;
    public boolean fancyClock;
    public boolean useGL13;
    public boolean useScratchTexture;
    public boolean hdFont;
    public boolean nonHDFontWidth;
    public boolean mipmap;

    // RANDOM_MOBS
    public boolean randomMobsEnabled;
    public String randomMobsLoggingLevel;
    public boolean leashLine;

    // BETTER_SKIES
    public boolean betterSkiesEnabled;
    public String betterSkiesLoggingLevel;
    public int horizon;
    public boolean brightenFireworks;
    public boolean skybox;
    public boolean unloadTextures;

    public enum Category {

        CUSTOM_COLORS,
        CUSTOM_ITEM_TEXTURES,
        CONNECTED_TEXTURES,
        EXTENDED_HD,
        RANDOM_MOBS,
        BETTER_SKIES;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public static MCPatcherForgeConfig instance() {
        if (config == null) {
            config = new MCPatcherForgeConfig(new File(Launch.minecraftHome, "config/mcpatcherforge.cfg"));
        }
        return config;
    }

    public MCPatcherForgeConfig(File file) {
        Configuration config = new Configuration(file);

        // spotless:off

        customColorsEnabled = config.get(Category.CUSTOM_COLORS.toString(),"enabled",true,"Enable the custom colors module").getBoolean();
        customColorsLoggingLevel = config.get(Category.CUSTOM_COLORS.toString(),"logging",Level.INFO.getName(),"logging level").getString();
        yVariance = config.get(Category.CUSTOM_COLORS.toString(),"yVariance",0).getInt();
        blockBlendRadius = config.get(Category.CUSTOM_COLORS.toString(),"blockBlendRadius",4).getInt();
        fogBlendRadius = config.get(Category.CUSTOM_COLORS.toString(),"fogBlendRadius",7).getInt();
        swampColors = config.get(Category.CUSTOM_COLORS.toString(),"swampColors",true).getBoolean();
        ccWater = config.get(Category.CUSTOM_COLORS.toString(),"water",true).getBoolean();
        ccTree = config.get(Category.CUSTOM_COLORS.toString(),"tree",true).getBoolean();
        ccRedstone = config.get(Category.CUSTOM_COLORS.toString(),"redstone",true).getBoolean();
        ccStem = config.get(Category.CUSTOM_COLORS.toString(),"stem",true).getBoolean();
        ccOtherBlocks = config.get(Category.CUSTOM_COLORS.toString(),"otherBlocks",true).getBoolean();
        smoothBiomes = config.get(Category.CUSTOM_COLORS.toString(),"smoothBiomes",true).getBoolean();
        testColorSmoothing = config.get(Category.CUSTOM_COLORS.toString(),"testColorSmoothing",false).getBoolean();
        ccPotion = config.get(Category.CUSTOM_COLORS.toString(),"potion",true).getBoolean();
        ccParticle = config.get(Category.CUSTOM_COLORS.toString(),"particle",true).getBoolean();
        ccFog = config.get(Category.CUSTOM_COLORS.toString(),"fog",true).getBoolean();
        ccClouds = config.get(Category.CUSTOM_COLORS.toString(),"clouds",true).getBoolean();
        ccMap = config.get(Category.CUSTOM_COLORS.toString(),"map",true).getBoolean();
        ccDye = config.get(Category.CUSTOM_COLORS.toString(),"dye",true).getBoolean();
        ccText = config.get(Category.CUSTOM_COLORS.toString(),"text",true).getBoolean();
        ccXPOrb = config.get(Category.CUSTOM_COLORS.toString(),"xporb",true).getBoolean();
        ccEgg = config.get(Category.CUSTOM_COLORS.toString(),"egg",true).getBoolean();
        ccLightmaps = config.get(Category.CUSTOM_COLORS.toString(),"lightmaps",true).getBoolean();

        customItemTexturesEnabled = config.get(Category.CUSTOM_ITEM_TEXTURES.toString(),"enabled",true,"Enable the custom item textures module").getBoolean();
        customItemTexturesLoggingLevel = config.get(Category.CUSTOM_ITEM_TEXTURES.toString(),"logging",Level.INFO.getName(),"logging level").getString();
        citItems = config.get(Category.CUSTOM_ITEM_TEXTURES.toString(),"items",true).getBoolean();
        citEnchantments = config.get(Category.CUSTOM_ITEM_TEXTURES.toString(),"enchantments",true).getBoolean();
        citArmor = config.get(Category.CUSTOM_ITEM_TEXTURES.toString(),"armor",true).getBoolean();

        connectedTexturesEnabled = config.get(Category.CONNECTED_TEXTURES.toString(),"enabled",true,"Enable the connected textures module").getBoolean();
        connectedTexturesLoggingLevel = config.get(Category.CONNECTED_TEXTURES.toString(),"logging",Level.INFO.getName(),"logging level").getString();
        maxRecursion = config.get(Category.CONNECTED_TEXTURES.toString(),"maxRecursion",4).getInt();
        debugTextures = config.get(Category.CONNECTED_TEXTURES.toString(),"debugTextures",false).getBoolean();
        betterGrass = config.get(Category.CONNECTED_TEXTURES.toString(),"betterGrass",false).getBoolean();
        ctmStandard = config.get(Category.CONNECTED_TEXTURES.toString(),"standard",true).getBoolean();
        ctmNonStandard = config.get(Category.CONNECTED_TEXTURES.toString(),"nonStandard",true).getBoolean();
        ctmGlassPane = config.get(Category.CONNECTED_TEXTURES.toString(),"glassPane",false).getBoolean();

        extendedHDEnabled = config.get(Category.EXTENDED_HD.toString(),"enabled",true,"Enable the extended hd module").getBoolean();
        extendedHDLoggingLevel = config.get(Category.EXTENDED_HD.toString(),"logging",Level.INFO.getName(),"logging level").getString();
        maxMipMapLevel = config.get(Category.EXTENDED_HD.toString(),"maxMipMapLevel",3).getInt();
        anisotropicFiltering = config.get(Category.EXTENDED_HD.toString(),"anisotropicFiltering",1).getInt();
        lodBias = config.get(Category.EXTENDED_HD.toString(),"lod bias",0).getInt();

        animations = config.get(Category.EXTENDED_HD.toString(),"animations",true).getBoolean();
        fancyCompass = config.get(Category.EXTENDED_HD.toString(),"fancyCompass",true).getBoolean();
        fancyClock = config.get(Category.EXTENDED_HD.toString(),"fancyClock",true).getBoolean();
        useGL13 = config.get(Category.EXTENDED_HD.toString(),"useGL13",true).getBoolean();
        useScratchTexture = config.get(Category.EXTENDED_HD.toString(),"useScratchTexture",true).getBoolean();
        hdFont = config.get(Category.EXTENDED_HD.toString(),"HDFont",true).getBoolean();
        nonHDFontWidth = config.get(Category.EXTENDED_HD.toString(),"nonHDFontWidth",false).getBoolean();
        mipmap = config.get(Category.EXTENDED_HD.toString(),"mipmap",false).getBoolean();

        randomMobsEnabled = config.get(Category.RANDOM_MOBS.toString(),"enabled",true,"Enable the random mobs module").getBoolean();
        randomMobsLoggingLevel = config.get(Category.RANDOM_MOBS.toString(),"logging",Level.INFO.getName(),"logging level").getString();
        leashLine = config.get(Category.RANDOM_MOBS.toString(),"leashLine",true).getBoolean();

        betterSkiesEnabled = config.get(Category.BETTER_SKIES.toString(),"enabled",true,"Enable the better skies module").getBoolean();
        betterSkiesLoggingLevel = config.get(Category.BETTER_SKIES.toString(),"logging",Level.INFO.getName(),"logging level").getString();
        horizon = config.get(Category.BETTER_SKIES.toString(),"horizon",16).getInt();
        brightenFireworks = config.get(Category.BETTER_SKIES.toString(),"brightenFireworks",true).getBoolean();
        skybox = config.get(Category.BETTER_SKIES.toString(),"skybox",true).getBoolean();
        unloadTextures = config.get(Category.BETTER_SKIES.toString(),"unloadTextures",true).getBoolean();

        // spotless:on
        if (config.hasChanged()) config.save();
    }
}
