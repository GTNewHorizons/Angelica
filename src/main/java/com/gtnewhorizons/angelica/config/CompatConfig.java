package com.gtnewhorizons.angelica.config;

import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = "angelica", filename = "angelica-compat")
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
}
