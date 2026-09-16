package com.gtnewhorizons.angelica.loading;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.ITransformers;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.CompatConfig;
import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.loading.DependencyVerifier;
import com.gtnewhorizons.angelica.glsm.loading.Lwjgl3ifyExclusions;
import com.gtnewhorizons.angelica.loading.fml.compat.CompatHandlers;
import com.gtnewhorizons.angelica.lwjgl3.MissingDependencySdl;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import com.gtnewhorizons.angelica.mixins.Mixins;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import jss.notfine.asm.AsmTransformers;
import jss.notfine.asm.mappings.Namer;
import jss.notfine.config.MCPatcherForgeConfig;
import jss.notfine.config.NotFineConfig;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

// this is not the real IFMLLoadingPlugin, if you put any annotations
// here it won't do anything, that's why we register transformer
// exclusions manually in the constructor
public final class AngelicaClientTweaker implements IFMLLoadingPlugin, IEarlyMixinLoader {

    public static final Logger LOGGER = LogManager.getLogger("Angelica");
    private static Boolean OBF_ENV;
    private String[] transformerClasses;

    public AngelicaClientTweaker() {
        Launch.classLoader.addTransformerExclusion("jss.notfine.asm");
        Launch.classLoader.addTransformerExclusion("com.gtnewhorizons.angelica.loading");
        Launch.classLoader.addTransformerExclusion("com.gtnewhorizons.angelica.glsm.GLStateManager");

        final boolean rfbLoaded = Launch.blackboard.getOrDefault("angelica.rfbPluginLoaded", Boolean.FALSE) == Boolean.TRUE;
        if (rfbLoaded) {
            Lwjgl3ifyExclusions.apply();
        } else {
            // Fucking java 8 and non RFB
            try {
                Class.forName("com.gtnewhorizon.gtnhlib.core.GTNHLibCore", true, Launch.classLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }

        try {
            // Angelica Config
            ConfigurationManager.registerConfig(AngelicaConfig.class);
            ConfigurationManager.registerConfig(CompatConfig.class);
            ConfigurationManager.registerConfig(FontConfig.class);
            MCPatcherForgeConfig.registerConfig();
            final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            final Configuration config = ctx.getConfiguration();
            final LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            if (AngelicaConfig.enableDebugLogging) {
                loggerConfig.setLevel(Level.DEBUG);
            }
            ctx.updateLoggers();

            // Debug features
            AngelicaConfig.enableTestBlocks = SystemProperties.ENABLE_TEST_BLOCKS;

        } catch (ConfigException e) {
            throw new RuntimeException(e);
        }
        DependencyVerifier.verifyOrHalt(AngelicaTweaker.class, "Angelica",
            DependencyVerifier.gtnhLibChecks("Angelica"), LOGGER,
            (title, message) -> MissingDependencySdl.showFatal(title, message));

        if (SystemProperties.USE_SDL_GPU) {
            if (SDLGPUGate.isSDLGPUAvailable()) {
                LOGGER.info("SDL GPU window mode enabled");
            } else {
                LOGGER.warn("angelica.sdlgpu.enable=true but SDL GPU dependencies not available, falling back to GL");
            }
        }

        // Register a scoped redirector early so classes prematurely loaded by other coremods during discovery/injectData
        // (e.g. Mycelium using HookLoader.class.getName()) still get GL calls redirected to GLSM. Only targets specific mod
        // packages to avoid interfering with mixin delegation on game classes. AngelicaLateTweaker removes this registration once
        // mixins are setup.
        if (FMLLaunchHandler.side().isClient()) {
            if (!rfbLoaded) {
                final String transformer = "com.gtnewhorizons.angelica.loading.fml.transformers.EarlyRedirectorTransformer";
                FMLRelaunchLog.finer("Registering transformer %s", transformer);
                Launch.classLoader.registerTransformer(transformer);
            }
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        if (transformerClasses == null) {
            final List<String> transformers = new ArrayList<>();
            transformers.addAll(CompatHandlers.getTransformers());
            // Add NotFine transformers
            final List<String> notFineTransformers = Arrays.asList(ITransformers.getTransformers(AsmTransformers.class));
            if (!notFineTransformers.isEmpty()) Namer.initNames();
            transformers.addAll(notFineTransformers);
            final boolean rfbLoaded = Launch.blackboard.getOrDefault("angelica.rfbPluginLoaded", Boolean.FALSE) == Boolean.TRUE;
            if (!rfbLoaded) {
                transformers.add("com.gtnewhorizons.angelica.loading.fml.transformers.IsbrhTessellatorAbuseTransformer");
            }
            transformerClasses = transformers.toArray(new String[0]);
        }
        return transformerClasses;
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
    public void injectData(Map<String, Object> data) {
        OBF_ENV = (Boolean) data.get("runtimeDeobfuscationEnabled");
        // Directly add this to the MixinServiceLaunchWrapper tweaker's list of Tweak Classes
        final List<String> tweaks = GlobalProperties.get(MixinServiceLaunchWrapper.BLACKBOARD_KEY_TWEAKCLASSES);
        if (tweaks != null) {
            // Bridge transformer compat config to blackboard for AngelicaLateTweaker
            narrowTransformerConfig("DragonAPI", AngelicaConfig.transformerCompat.narrowDragonAPI);
            narrowTransformerConfig("Xaeros", AngelicaConfig.transformerCompat.narrowXaeros);
            narrowTransformerConfig("AdvancedLightsabers", AngelicaConfig.transformerCompat.narrowAdvancedLightsabers);
            narrowTransformerConfig("Alfheim", AngelicaConfig.transformerCompat.narrowAlfheim);
            narrowTransformerConfig("Ears", AngelicaConfig.transformerCompat.narrowEars);
            narrowTransformerConfig("FiskHeroes", AngelicaConfig.transformerCompat.narrowFiskHeroes);
            narrowTransformerConfig("FoamFix", AngelicaConfig.transformerCompat.narrowFoamFix);
            narrowTransformerConfig("LegendsMod", AngelicaConfig.transformerCompat.narrowLegendsMod);

            tweaks.add("com.gtnewhorizons.angelica.loading.fml.tweakers.IncompatibleModsDisablerTweaker");
            if (AngelicaConfig.enableHudCaching) {
                tweaks.add("com.gtnewhorizons.angelica.loading.fml.tweakers.XaerosTransformerDisablerTweaker");
            }
            if (FMLLaunchHandler.side().isClient()) {
                // We register ITweakers that will run last in order to register
                // specific IClassTransformers that will run last in the transformer chain.
                // If we were to register them normally in getASMTransformerClass(),
                // they would be sorted at index 0 which we do not want.
                final boolean rfbLoaded = Launch.blackboard.getOrDefault("angelica.rfbPluginLoaded", Boolean.FALSE) == Boolean.TRUE;
                if (!rfbLoaded) {
                    tweaks.add("com.gtnewhorizons.angelica.loading.fml.tweakers.AngelicaLateTweaker");
                    tweaks.add("com.gtnewhorizons.angelica.loading.fml.tweakers.CeleritasLateTweaker");
                }
            }
        }
    }

    /** Publish a transformer-narrowing toggle to the blackboard; read by AngelicaLateTweaker.narrowEnabled(). */
    private static void narrowTransformerConfig(String mod, boolean enabled) {
        Launch.blackboard.put("angelica.narrow." + mod, enabled);
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public String getMixinConfig() {
        final int v = Runtime.version().feature();
        if (v >= 21) return "mixins.angelica.early.j21.json";
        if (v >= 17) return "mixins.angelica.early.j17.json";
        return "mixins.angelica.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        NotFineConfig.loadSettings();
        //This may be possible to handle differently or fix.
        if (loadedCoreMods.contains("cofh.asm.LoadingPlugin")) {
            MCPatcherForgeConfig.ExtendedHD.hdFont = false;
        }
        return IMixins.getEarlyMixins(Mixins.class, loadedCoreMods);
    }

    /**
     * Returns true if we are in an obfuscated environment, returns false in dev environment.
     */
    public static boolean isObfEnv() {
        if (OBF_ENV == null) {
            throw new IllegalStateException("Obfuscation state has been accessed too early!");
        }
        return OBF_ENV;
    }

    /**
     * Returns the appropriate name according to current environment's obfuscation
     */
    public static String obf(String deobf, String obf) {
        if (OBF_ENV == null) {
            throw new IllegalStateException("Obfuscation state has been accessed too early!");
        }
        if (OBF_ENV) {
            return obf;
        }
        return deobf;
    }
}
