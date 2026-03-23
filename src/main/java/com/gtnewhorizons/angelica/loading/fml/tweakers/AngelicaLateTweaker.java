package com.gtnewhorizons.angelica.loading.fml.tweakers;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
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

            if (AngelicaConfig.transformerCompat.narrowDragonAPI && exceptions.remove("Reika.DragonAPI.ASM")) {
                // Re-add non-GL classes; ASMCallsClient and utility classes with GL calls are left exposed
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
                FMLRelaunchLog.info("[Angelica] Narrowed Reika.DragonAPI.ASM transformer exclusion to allow GL redirection");
            }

            if (AngelicaConfig.transformerCompat.narrowXaeros) {
                if (exceptions.remove("xaero.common.core")) {
                    exceptions.add("xaero.common.core.transformer");
                    FMLRelaunchLog.info("[Angelica] Narrowed xaero.common.core transformer exclusion to allow GL redirection");
                }
                if (exceptions.remove("xaero.map.core")) {
                    exceptions.add("xaero.map.core.transformer");
                    FMLRelaunchLog.info("[Angelica] Narrowed xaero.map.core transformer exclusion to allow GL redirection");
                }
            }

            if (AngelicaConfig.transformerCompat.narrowAdvancedLightsabers && exceptions.remove("com.fiskmods.lightsabers.asm")) {
                exceptions.add("com.fiskmods.lightsabers.asm.ALLoadingPlugin");
                exceptions.add("com.fiskmods.lightsabers.asm.ASMHooks");
                exceptions.add("com.fiskmods.lightsabers.asm.transformers");
                FMLRelaunchLog.info("[Angelica] Narrowed com.fiskmods.lightsabers.asm transformer exclusion to allow GL redirection");
            }

            if (AngelicaConfig.transformerCompat.narrowAlfheim && exceptions.remove("alfheim.common.core.asm.hook")) {
                exceptions.add("alfheim.common.core.asm.hook.AlfheimFieldHookHandler");
                exceptions.add("alfheim.common.core.asm.hook.AlfheimHPHooks");
                exceptions.add("alfheim.common.core.asm.hook.Botania18AndUpBackport");
                exceptions.add("alfheim.common.core.asm.hook.ElementalDamageAdapter");

                exceptions.add("alfheim.common.core.asm.hook.integration");

                exceptions.add("alfheim.common.core.asm.hook.extender.FurnaceExtender");
                exceptions.add("alfheim.common.core.asm.hook.extender.ItemAuraRingExtender");
                exceptions.add("alfheim.common.core.asm.hook.extender.ItemLensExtender");
                exceptions.add("alfheim.common.core.asm.hook.extender.ItemTwigWandExtender");
                exceptions.add("alfheim.common.core.asm.hook.extender.LightRelayExtender");
                exceptions.add("alfheim.common.core.asm.hook.extender.PureDaisyExtender");
                exceptions.add("alfheim.common.core.asm.hook.extender.QuartzExtender");
                exceptions.add("alfheim.common.core.asm.hook.extender.RelicHooks");
                exceptions.add("alfheim.common.core.asm.hook.extender.SparkExtender");

                exceptions.add("alfheim.common.core.asm.hook.fixes.BotaniaGlowingRenderFixes");
                exceptions.add("alfheim.common.core.asm.hook.fixes.CorporeaInputFix");
                exceptions.add("alfheim.common.core.asm.hook.fixes.GodAttributesHooks");
                exceptions.add("alfheim.common.core.asm.hook.fixes.RecipeAncientWillsFix");
                FMLRelaunchLog.info("[Angelica] Narrowed alfheim.common.core.asm.hook transformer exclusion to allow GL redirection");
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
