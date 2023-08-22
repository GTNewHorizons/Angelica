package com.gtnewhorizons.angelica.transform;

import net.minecraft.launchwrapper.Launch;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;

public class InitNames {

    public static void init() {
        final boolean obfuscated = !(Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");
        AngelicaTweaker.LOGGER.info("Environment obfuscated: {}", obfuscated);
        if (obfuscated) {
            new NamerSrg().setNames();
        } else {
            new NamerMcp().setNames();
        }
    }
}
