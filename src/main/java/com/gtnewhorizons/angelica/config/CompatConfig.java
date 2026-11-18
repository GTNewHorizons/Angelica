package com.gtnewhorizons.angelica.config;

import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = "angelica", filename = "angelica-compat", category = "compat")
public class CompatConfig {

    @Config.Comment("Apply fixes to the LOTR mod")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean fixLotr;

    @Config.Comment("Apply fixes to Extra Utilities")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean fixExtraUtils;

    @Config.Comment("Apply fixes to Stacks on Stacks")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean fixStacksOnStacks;

    @Config.Comment("Redirect KaizPatchX script GL calls through Angelica's GLRedirects API")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean fixKaizPatchX;

    @Config.Comment("Apply fixes to Minefactory Reloaded")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean fixMinefactoryReloaded;

    @Config.Comment("Apply fixes to Thaumcraft")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean fixThaumcraft;

    @Config.Comment("Apply fixes to ThaumicHorizons")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean fixThaumicHorizons;

    @Config.Comment("Apply fixes to Immersive Engineering")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean fixImmersiveEngineering;

    @Config.Comment("Apply fixes to Security Craft")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean fixSecurityCraft;
    
    @Config.Comment("Apply fixes to NTM:Space")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean fixNTMSpace;

    @Config.Comment({"Rules for F3 lines that other mods add without Angelica's F3 API, one rule per entry.",
        "A rule matches lines that start with the quoted text (color codes ignored), in the left or right column.",
        "  drop left \"Text: \"                   hides the line",
        "  fact left \"Text: \" group=<panel id>  moves the line into that F3 panel; add name=\"Label\" to rename it",
        "Panel ids are listed under Panels in the F3+F6 screen; entry ids show when you hover an entry there.",
        "Add when=<entry id> to apply a rule only while that F3 entry is enabled."})
    @Config.DefaultStringList({})
    public static String[] debugLineRules;

}
