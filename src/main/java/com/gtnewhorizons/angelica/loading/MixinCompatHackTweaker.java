package com.gtnewhorizons.angelica.loading;

import com.gtnewhorizons.angelica.transform.AClassTransformer;
import com.gtnewhorizons.angelica.transform.GLStateManagerTransformer;
import com.gtnewhorizons.angelica.transform.TessellatorTransformer;
import cpw.mods.fml.common.asm.transformers.TerminalTransformer;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;


public class MixinCompatHackTweaker implements ITweaker {

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        LaunchClassLoader lcl = Launch.classLoader;
        try {
            Field xformersField = lcl.getClass().getDeclaredField("transformers");
            xformersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<IClassTransformer> xformers = (List<IClassTransformer>) xformersField.get(lcl);
            int terminalIndex;
            for (terminalIndex = 1; terminalIndex < xformers.size(); terminalIndex++) {
                if (xformers.get(terminalIndex) instanceof TerminalTransformer) {
                    break;
                }
            }

            try {
                Class.forName("me.eigenraven.lwjgl3ify.core.LwjglRedirectTransformer");
                AngelicaTweaker.LOGGER.info("LwjglRedirectTransformer found, injecting before it");
                terminalIndex -= 2;

            } catch(Exception ignored) {
                AngelicaTweaker.LOGGER.info("LwjglRedirectTransformer not found, injecting near the end");
                terminalIndex -= 1;
            }

            xformers.add(terminalIndex, new AClassTransformer());
            AngelicaTweaker.LOGGER.info("Hacked in asm class transformer in position {}", terminalIndex);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        // Run after Mixins, but hopefully before LWJGl3ify
        Launch.classLoader.registerTransformer(GLStateManagerTransformer.class.getName());
        Launch.classLoader.registerTransformer(TessellatorTransformer.class.getName());
        return new String[0];
    }
}
