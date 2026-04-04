package com.gtnewhorizons.umbra.loading.fml.tweakers;

import com.gtnewhorizons.angelica.glsm.loading.EcosystemNarrowRules;
import com.gtnewhorizons.angelica.glsm.loading.TransformerNarrower;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/**
 * Late tweaker that:
 * 1. Narrows overly-broad transformer exclusions from other coremods
 * 2. Removes the scoped early redirector
 * 3. Registers the full redirector in its proper post-mixin position
 */
public class UmbraLateTweaker implements ITweaker {

    private static final String EARLY_REDIRECTOR_CLASS = "com.gtnewhorizons.umbra.loading.fml.transformers.EarlyRedirectorTransformer";

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        narrowTransformerExclusions(classLoader);
    }

    @SuppressWarnings("unchecked")
    private static void narrowTransformerExclusions(LaunchClassLoader classLoader) {
        try {
            final Field exceptionsField = Launch.classLoader.getClass().getDeclaredField("transformerExceptions");
            exceptionsField.setAccessible(true);
            final Set<String> exceptions = (Set<String>) exceptionsField.get(classLoader);

            TransformerNarrower.narrow(exceptions, Launch.blackboard, "umbra", "Umbra",
                EcosystemNarrowRules.ALL);

        } catch (Exception e) {
            FMLRelaunchLog.warning("[Umbra] Failed to narrow transformer exclusions: %s", e.getMessage());
        }
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String[] getLaunchArguments() {
        // Remove the scoped early redirector and register the full one after mixins
        try {
            final Field transformersField = Launch.classLoader.getClass().getDeclaredField("transformers");
            transformersField.setAccessible(true);
            final List<IClassTransformer> transformers =
                (List<IClassTransformer>) transformersField.get(Launch.classLoader);
            transformers.removeIf(t -> t.getClass().getName().equals(EARLY_REDIRECTOR_CLASS));
        } catch (Exception e) {
            FMLRelaunchLog.warning("[Umbra] Failed to remove EarlyRedirectorTransformer: %s", e.getMessage());
        }

        final String transformer =
            "com.gtnewhorizons.umbra.loading.fml.transformers.UmbraRedirectorTransformer";
        FMLRelaunchLog.finer("[Umbra] Registering transformer %s", transformer);
        Launch.classLoader.registerTransformer(transformer);
        return new String[0];
    }
}
