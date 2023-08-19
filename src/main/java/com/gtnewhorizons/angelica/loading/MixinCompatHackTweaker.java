package com.gtnewhorizons.angelica.loading;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import com.gtnewhorizons.angelica.ALog;
import com.gtnewhorizons.angelica.transform.AClassTransformer;

import cpw.mods.fml.common.asm.transformers.TerminalTransformer;

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
            xformers.add(terminalIndex - 1, new AClassTransformer());
            ALog.info("Hacked in asm class transformer in position %d", terminalIndex - 1);
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
        return new String[0];
    }
}
