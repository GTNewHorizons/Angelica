package com.gtnewhorizons.angelica.loading;

import com.gtnewhorizons.angelica.transform.RedirectorTransformer;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.util.List;


public class MixinCompatHackTweaker implements ITweaker {

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {

    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        // no-op
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    public String[] getLaunchArguments() {
        // Run after Mixins, but before LWJGl3ify
        Launch.classLoader.registerTransformer(RedirectorTransformer.class.getName());
        return new String[0];
    }
}
