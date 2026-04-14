package com.gtnewhorizons.umbra.loading;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizons.angelica.glsm.loading.DependencyVerifier;
import com.gtnewhorizons.angelica.glsm.loading.EcosystemNarrowRules;
import com.gtnewhorizons.retrofuturabootstrap.SharedConfig;
import com.gtnewhorizons.umbra.loading.shared.AngelicaDetector;
import com.gtnewhorizons.umbra.mixins.Mixins;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side coremod logic. Handles Angelica detection, transformer exclusions,
 * mixin registration, dependency verification, and late tweaker registration.
 */
public class UmbraClientTweaker implements IFMLLoadingPlugin, IEarlyMixinLoader {

    private static Boolean OBF_ENV;
    private final boolean disabled;

    @SuppressWarnings("unchecked")
    public UmbraClientTweaker() {
        Launch.classLoader.addTransformerExclusion("com.gtnewhorizons.umbra.loading");
        Launch.classLoader.addClassLoaderExclusion("com.gtnewhorizons.angelica.glsm.redirect");

        // Detect Angelica -- if present, disable Umbra entirely
        if (AngelicaDetector.isPresent()) {
            FMLRelaunchLog.info("[Umbra] Angelica detected, Disabling Umbra");
            Launch.blackboard.put("umbra.disabled", Boolean.TRUE);
            disabled = true;
            return;
        }

        disabled = false;

        if (Boolean.TRUE.equals(Launch.blackboard.get("umbra.rfbPluginLoaded"))) {
            addLwjgl3ifyExclusions();
        }

        verifyDependencies();

        // Register early redirector if RFB is not loaded (RFB handles its own registration)
        if (!Boolean.TRUE.equals(Launch.blackboard.get("umbra.rfbPluginLoaded"))) {
            final String earlyTransformer = "com.gtnewhorizons.umbra.loading.fml.transformers.EarlyRedirectorTransformer";
            Launch.classLoader.registerTransformer(earlyTransformer);
            FMLRelaunchLog.info("[Umbra] Registered early redirector transformer");
        }
    }

    private static void addLwjgl3ifyExclusions() {
        final var handle = SharedConfig.getRfbTransformers().stream()
            .filter(transformer -> transformer.id().equals("lwjgl3ify:redirect"))
            .findFirst()
            .orElse(null);
        if (handle != null) {
            for (String exclusion : EcosystemNarrowRules.LWJGL3IFY_EXCLUSIONS_SHARED) {
                handle.exclusions().add(exclusion);
            }
        }
    }

    private static void verifyDependencies() {
        DependencyVerifier.verify(UmbraClientTweaker.class, List.of(
            new DependencyVerifier.Check(
                "/com/gtnewhorizon/gtnhlib/client/renderer/PrimitiveExtractor.class",
                "Missing dependency: Umbra requires GTNHLib 0.8.21+! Download: https://modrinth.com/mod/gtnhlib"),
            new DependencyVerifier.Check(
                "/xyz/wagyourtail/jvmdg/exc/MissingStubError.class",
                "GTNHLib is outdated: Umbra requires GTNHLib 0.9.0+! Download: https://modrinth.com/mod/gtnhlib")
        ));
    }

    @Override
    public String[] getASMTransformerClass() {
        if (disabled) return new String[0];
        return new String[0]; // Redirector registered via late tweaker, not here
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void injectData(Map<String, Object> data) {
        if (disabled) return;

        OBF_ENV = (Boolean) data.get("runtimeDeobfuscationEnabled");

        // Register the late tweaker (handles narrowing + full redirector registration)
        if (!Boolean.TRUE.equals(Launch.blackboard.get("umbra.rfbPluginLoaded"))) {
            final List<String> tweakClasses = org.spongepowered.asm.launch.GlobalProperties.get(
                MixinServiceLaunchWrapper.BLACKBOARD_KEY_TWEAKCLASSES);
            if (tweakClasses != null) {
                tweakClasses.add("com.gtnewhorizons.umbra.loading.fml.tweakers.UmbraLateTweaker");
                FMLRelaunchLog.info("[Umbra] Registered late tweaker");
            }
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public String getMixinConfig() {
        if (disabled) return null;
        return "mixins.umbra.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        if (disabled) return Collections.emptyList();
        return IMixins.getEarlyMixins(Mixins.class, loadedCoreMods);
    }

    public static boolean isObfEnv() {
        if (OBF_ENV == null) throw new IllegalStateException("OBF_ENV not yet initialized");
        return OBF_ENV;
    }
}
