package com.gtnewhorizons.angelica.config;

import org.embeddedt.archaicfix.config.Config;

@Config(modid = "angelica")
public class AngelicaConfig {

    @Config.Comment("Enable Sodium rendering")
    @Config.DefaultBoolean(true)
    @Config.RequiresMcRestart
    public static boolean enableSodium;

    @Config.Comment("Enable Iris Shaders")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean enableIris;


}
