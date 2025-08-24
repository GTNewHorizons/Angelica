package com.gtnewhorizons.angelica.loading;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.ITransformers;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.CompatConfig;
import com.gtnewhorizons.angelica.mixins.Mixins;
import com.gtnewhorizons.angelica.loading.fml.compat.CompatHandlers;
import com.gtnewhorizons.retrofuturabootstrap.SharedConfig;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
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
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ================== Important ==================
// Due to a bug caused by this class both implementing
// IFMLLoadingPlugin and IEarlyMixinLoader,
// the IClassTransformer registered in this class
// will not respect the sorting index defined.
// They will instead use default index 0 which means they will see
// obfuscated mappings and not SRG mappings when running outside of dev env.
// ===============================================
//@IFMLLoadingPlugin.SortingIndex(Integer.MAX_VALUE - 5)
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({
    "jss.notfine.asm",
    "com.gtnewhorizons.angelica.loading",
    "com.gtnewhorizons.angelica.glsm.GLStateManager"})
public class AngelicaTweaker implements IFMLLoadingPlugin, IEarlyMixinLoader {

    private static Boolean OBF_ENV;
    public static final Logger LOGGER = LogManager.getLogger("Angelica");

    private String[] transformerClasses;

    public AngelicaTweaker() {
        try {
            // Angelica Config
            ConfigurationManager.registerConfig(AngelicaConfig.class);
            ConfigurationManager.registerConfig(CompatConfig.class);
            MCPatcherForgeConfig.registerConfig();
            final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            final Configuration config = ctx.getConfiguration();
            final LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            if (AngelicaConfig.enableDebugLogging) {
                loggerConfig.setLevel(Level.DEBUG);
            }
            ctx.updateLoggers();

            // Debug features
            AngelicaConfig.enableTestBlocks = Boolean.getBoolean("angelica.enableTestBlocks");
            if(AngelicaConfig.enableCeleritas && AngelicaConfig.enableSodium) {
                LOGGER.warn("Both Celeritas and Sodium are enabled. Disabling Sodium.");
                AngelicaConfig.enableSodium = false;
            }

            if(AngelicaConfig.enableIris && !AngelicaConfig.enableSodium) {
                LOGGER.warn("Iris Shaders require Sodium to be enabled. Disabling Iris Shaders.");
                AngelicaConfig.enableIris = false;
            }

            if(AngelicaConfig.enableDistantHorizons && !AngelicaConfig.enableIris) {
                LOGGER.warn("DH fading requires Iris to be enabled. Disabling DH fadings.");
                CeleritasWorldRenderer.enableDHFading = false;
            }

        } catch (ConfigException e) {
            throw new RuntimeException(e);
        }
        verifyDependencies();
    }

    private static void verifyDependencies() {
        if (AngelicaTweaker.class.getResource("/it/unimi/dsi/fastutil/ints/Int2ObjectMap.class") == null) {
            throw new RuntimeException("Missing dependency: Angelica requires GTNHLib 0.2.1 or newer! Download: https://modrinth.com/mod/gtnhlib");
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
            tweaks.add("com.gtnewhorizons.angelica.loading.fml.tweakers.IncompatibleModsDisablerTweaker");
            if (AngelicaConfig.enableHudCaching) {
                tweaks.add("com.gtnewhorizons.angelica.loading.fml.tweakers.XaerosTransformerDisablerTweaker");
            }
            if (FMLLaunchHandler.side().isClient()) {
                // We register ITweakers that will run last in order to register
                // specific IClassTransformers that will run last in the transformer chain.
                // If we were to register them normally in getASMTransformerClass(),
                // they would be sorted at index 0 which we do not want.
                boolean rfbLoaded = Launch.blackboard.getOrDefault("angelica.rfbPluginLoaded", Boolean.FALSE) == Boolean.TRUE;
                if (!rfbLoaded) {
                    tweaks.add("com.gtnewhorizons.angelica.loading.fml.tweakers.AngelicaLateTweaker");
                }
                if (AngelicaConfig.enableSodium) {
                    tweaks.add("com.gtnewhorizons.angelica.loading.fml.tweakers.SodiumLateTweaker");
                }
            }
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public String getMixinConfig() {
        // Temporarily until the common module defines Lwjg3ify-aware
        final var handle = SharedConfig.getRfbTransformers().stream().filter(transformer -> transformer.id().equals("lwjgl3ify:redirect")).findFirst().orElseThrow();
        handle.exclusions().add("org.embeddedt.embeddium");
        handle.exclusions().add("org.taumc.celeritas");

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
