package com.gtnewhorizons.angelica.transform;

import net.minecraft.launchwrapper.Launch;

import com.gtnewhorizons.angelica.ALog;

public class InitNames {

    public static void init() {
        final boolean obfuscated = !(Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");
        ALog.info("Environment obfuscated: %s", obfuscated);
        if (obfuscated) {
            new NamerSrg().setNames();
        } else {
            new NamerMcp().setNames();
        }
    }
}
