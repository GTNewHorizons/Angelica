package com.gtnewhorizons.angelica.loading.fml.tweakers;

import cpw.mods.fml.relauncher.FMLRelaunchLog;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

public class AngelicaLateTweaker implements ITweaker {

    private static final String EARLY_REDIRECTOR_CLASS = "com.gtnewhorizons.angelica.loading.fml.transformers.EarlyRedirectorTransformer";

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        // no-op
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        narrowTransformerExclusions(classLoader);
    }

    /**
     * Reduces overly broad transformer exclusions that prevent AngelicaRedirector from doing its job.
     *
     * Looking at you, DragonAPI.
     */
    @SuppressWarnings("unchecked")
    private static void narrowTransformerExclusions(LaunchClassLoader classLoader) {
        try {
            final Field exceptionsField = Launch.classLoader.getClass().getDeclaredField("transformerExceptions");
            exceptionsField.setAccessible(true);
            final Set<String> exceptions = (Set<String>) exceptionsField.get(classLoader);

            if (exceptions.remove("Reika.DragonAPI.ASM")) {
                // Don't read ASMCallsClient and Patchers/Fixes/WorldRenderAlpha.  The rest are fair game.

                // Re-add the other non utility classes
                exceptions.add("Reika.DragonAPI.ASM.Patchers");
                exceptions.add("Reika.DragonAPI.ASM.Profiling");
                exceptions.add("Reika.DragonAPI.ASM.APIStripper");
                exceptions.add("Reika.DragonAPI.ASM.ClassReparenter");
                exceptions.add("Reika.DragonAPI.ASM.DependentMethodStripper");
                exceptions.add("Reika.DragonAPI.ASM.DragonAPIClassTransformer");
                exceptions.add("Reika.DragonAPI.ASM.FMLItemBlockPatch");
                exceptions.add("Reika.DragonAPI.ASM.FluidNamePatch");
                exceptions.add("Reika.DragonAPI.ASM.InterfaceInjector");
                exceptions.add("Reika.DragonAPI.ASM.SpecialBiomePlacement");
                exceptions.add("Reika.DragonAPI.ASM.StructureLootHooks");
                FMLRelaunchLog.info("[Angelica] Narrowed Reika.DragonAPI.ASM transformer exclusion to allow GL redirection of utility classes");
            }

            if (exceptions.remove("xaero.common.core")) {
                exceptions.add("xaero.common.core.transformer");
                FMLRelaunchLog.info("[Angelica] Narrowed xaero.common.core transformer exclusion to allow GL redirection");
            }
            if (exceptions.remove("xaero.map.core")) {
                exceptions.add("xaero.map.core.transformer");
                FMLRelaunchLog.info("[Angelica] Narrowed xaero.map.core transformer exclusion to allow GL redirection");
            }
        } catch (Exception e) {
            FMLRelaunchLog.warning("[Angelica] Failed to narrow transformer exclusions: %s", e.getMessage());
        }
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String[] getLaunchArguments() {
        // Remove the scoped early redirector (no longer needed) and register the full one in its proper position: after mixins
        try {
            final Field transformersField = Launch.classLoader.getClass().getDeclaredField("transformers");
            transformersField.setAccessible(true);
            final List<IClassTransformer> transformers = (List<IClassTransformer>) transformersField.get(Launch.classLoader);

            transformers.removeIf(t -> t.getClass().getName().equals(EARLY_REDIRECTOR_CLASS));
        } catch (Exception e) {
            FMLRelaunchLog.warning("[Angelica] Failed to remove EarlyRedirectorTransformer: %s", e.getMessage());
        }

        final String transformer = "com.gtnewhorizons.angelica.loading.fml.transformers.AngelicaRedirectorTransformer";
        FMLRelaunchLog.finer("Registering transformer %s", transformer);
        Launch.classLoader.registerTransformer(transformer);
        return new String[0];
    }
}
