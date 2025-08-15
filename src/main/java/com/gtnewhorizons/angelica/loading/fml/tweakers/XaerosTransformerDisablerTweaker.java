package com.gtnewhorizons.angelica.loading.fml.tweakers;

import cpw.mods.fml.relauncher.FMLRelaunchLog;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

public class XaerosTransformerDisablerTweaker implements ITweaker {

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        // no-op
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        try {
            final Field xformersField = classLoader.getClass().getDeclaredField("transformers");
            xformersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<IClassTransformer> transformers = (List<IClassTransformer>) xformersField.get(classLoader);
            for (int idx = transformers.size() - 1; idx >= 0; idx--) {
                final String name = transformers.get(idx).getClass().getName();
                if (name.startsWith("xaero.common.core.transformer.GuiIngameForgeTransformer")) {
                    transformers.remove(idx);
                    FMLRelaunchLog.info("[Angelica/XaerosTransformerDisablerTweaker] Removed transformer " + name);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
