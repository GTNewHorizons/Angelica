package com.gtnewhorizons.angelica.loading;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.transform.BlockTransformer;
import com.gtnewhorizons.angelica.transform.RedirectorTransformer;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.LOGGER;

public class MixinCompatHackTweaker implements ITweaker {
    public static final boolean DISABLE_OPTIFINE_AND_FASTCRAFT = true;
    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        verifyDependencies();
        
        if(DISABLE_OPTIFINE_AND_FASTCRAFT) {
            LOGGER.info("Disabling Optifine and Fastcraft (if present)");
            disableOptifineAndFastcraft();
        }
    }
    
    private void verifyDependencies() {
        if(MixinCompatHackTweaker.class.getResource("/it/unimi/dsi/fastutil/ints/Int2ObjectMap.class") == null) {
            throw new RuntimeException("Missing dependency: Angelica requires GTNHLib 0.2.1 or newer! Download: https://modrinth.com/mod/gtnhlib");
        }
    }

    private void disableOptifineAndFastcraft() {
        // Remove Optifine and Fastcraft transformers & Mod Containers
        try {
            LaunchClassLoader lcl = Launch.classLoader;
            Field xformersField = lcl.getClass().getDeclaredField("transformers");
            xformersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<IClassTransformer> xformers = (List<IClassTransformer>) xformersField.get(lcl);
            for (int idx = xformers.size() - 1; idx >= 0; idx--) {
                final String name = xformers.get(idx).getClass().getName();
                if (name.startsWith("optifine") || name.startsWith("fastcraft")) {
                    LOGGER.info("Removing transformer " + name);
                    xformers.remove(idx);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            Field injectedContainersField = Loader.class.getDeclaredField("injectedContainers");
            injectedContainersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String> containers = (List<String> ) injectedContainersField.get(Loader.class);
            for (int idx = containers.size() - 1; idx >= 0; idx--) {
                final String name = containers.get(idx);
                if (name.startsWith("optifine") || name.startsWith("fastcraft")) {
                    LOGGER.info("Removing mod container " + name);
                    containers.remove(idx);
                }
            }
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
        if (FMLLaunchHandler.side().isClient()) {
            // Run after Mixins, but before LWJGl3ify
            Launch.classLoader.registerTransformer(RedirectorTransformer.class.getName());
            if(AngelicaConfig.enableSodium) {
                Launch.classLoader.registerTransformer(BlockTransformer.class.getName());
            }
        }

        return new String[0];
    }
}
