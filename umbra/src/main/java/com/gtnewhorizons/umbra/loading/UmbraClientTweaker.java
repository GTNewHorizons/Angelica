package com.gtnewhorizons.umbra.loading;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.loading.DependencyVerifier;
import com.gtnewhorizons.angelica.glsm.loading.Lwjgl3ifyExclusions;
import com.gtnewhorizons.angelica.lwjgl3.MissingDependencySdl;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import com.gtnewhorizons.umbra.loading.shared.AngelicaDetector;
import com.gtnewhorizons.umbra.mixins.Mixins;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    public static final Logger LOGGER = LogManager.getLogger("Umbra");
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
            Lwjgl3ifyExclusions.apply();
        }

        DependencyVerifier.verifyOrHalt(UmbraClientTweaker.class, "Umbra",
            DependencyVerifier.gtnhLibChecks("Umbra"), LOGGER,
            (title, message) -> MissingDependencySdl.showFatal(title, message));

        if (SystemProperties.USE_SDL_GPU) {
            if (SDLGPUGate.isSDLGPUAvailable()) {
                LOGGER.info("SDL GPU window mode enabled");
            } else {
                LOGGER.warn("angelica.sdlgpu.enable=true but SDL GPU dependencies not available, falling back to GL");
            }
        }

        // Register early redirector if RFB is not loaded (RFB handles its own registration)
        if (!Boolean.TRUE.equals(Launch.blackboard.get("umbra.rfbPluginLoaded"))) {
            final String earlyTransformer = "com.gtnewhorizons.umbra.loading.fml.transformers.EarlyRedirectorTransformer";
            Launch.classLoader.registerTransformer(earlyTransformer);
            FMLRelaunchLog.info("[Umbra] Registered early redirector transformer");
        }
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
