package com.gtnewhorizons.angelica.loading;

import com.google.common.collect.ImmutableMap;
import com.gtnewhorizon.gtnhlib.asm.ASMUtil;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.CompatConfig;
import com.gtnewhorizons.angelica.mixins.Mixins;
import com.gtnewhorizons.angelica.mixins.TargetedMod;
import com.gtnewhorizons.angelica.transform.compat.GenericCompatTransformer;
import com.gtnewhorizons.angelica.transform.compat.handlers.CompatHandler;
import com.gtnewhorizons.angelica.transform.compat.handlers.CompatHandlers;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import jss.notfine.asm.AsmTransformers;
import jss.notfine.asm.mappings.Namer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper;

import java.util.ArrayList;
import java.util.HashSet;
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
        "com.gtnewhorizons.angelica.transform.RedirectorTransformer",
        "com.gtnewhorizons.angelica.glsm.GLStateManager"})
public class AngelicaTweaker implements IFMLLoadingPlugin, IEarlyMixinLoader {

    private static final boolean DUMP_CLASSES = Boolean.parseBoolean(System.getProperty("angelica.dumpClass", "false"));
    private static boolean OBF_ENV;
    public static final Logger LOGGER = LogManager.getLogger("Angelica");

    private String[] transformerClasses;

    static {
        try {
            // Angelica Config
            ConfigurationManager.registerConfig(AngelicaConfig.class);
            ConfigurationManager.registerConfig(CompatConfig.class);
            final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            final Configuration config = ctx.getConfiguration();
            final LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            if (AngelicaConfig.enableDebugLogging) {
                loggerConfig.setLevel(Level.DEBUG);
            }
            ctx.updateLoggers();

            // Debug features
            AngelicaConfig.enableTestBlocks = Boolean.parseBoolean(System.getProperty("angelica.enableTestBlocks", "false"));

        } catch (ConfigException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        // Directly add this to the MixinServiceLaunchWrapper tweaker's list of Tweak Classes
        final List<String> mixinTweakClasses = GlobalProperties.get(MixinServiceLaunchWrapper.BLACKBOARD_KEY_TWEAKCLASSES);
        if (mixinTweakClasses != null) {
            mixinTweakClasses.add(MixinCompatHackTweaker.class.getName());
        }
        if (transformerClasses == null) {
            final List<String> transformers = new ArrayList<>();

            // Regsiter compat handlers, and add extra specific transformers, then build and register the generic transformer
            for (CompatHandler handler : CompatHandlers.getHandlers()) {
                GenericCompatTransformer.register(handler);
                if (handler.extraTransformers() != null) {
                    transformers.addAll(handler.extraTransformers());
                }
            }

            GenericCompatTransformer.build();
            transformers.add("com.gtnewhorizons.angelica.transform.compat.GenericCompatTransformer");

            // Add NotFine transformers
            final List<String> notFineTransformers = AsmTransformers.getTransformers();
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
        OBF_ENV = (boolean) data.get("runtimeDeobfuscationEnabled");
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public String getMixinConfig() {
        return "mixins.angelica.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return Mixins.getEarlyMixins(loadedCoreMods);
    }

    public static boolean DUMP_CLASSES() {
        return DUMP_CLASSES || !OBF_ENV;
    }

    /**
     * Returns true if we are in an obfuscated environment, returns false in dev environment.
     */
    public static boolean isObfEnv() {
        return OBF_ENV;
    }

    public static void dumpClass(String className, byte[] originalBytes, byte[] transformedBytes, Object transformer) {
        if (AngelicaTweaker.DUMP_CLASSES()) {
            ASMUtil.saveAsRawClassFile(originalBytes, className + "_PRE", transformer);
            ASMUtil.saveAsRawClassFile(transformedBytes, className + "_POST", transformer);
        }
    }

    private static final ImmutableMap<String, TargetedMod> MODS_BY_CLASS = ImmutableMap.<String, TargetedMod>builder()
        .put("optifine.OptiFineForgeTweaker", TargetedMod.OPTIFINE)
        .put("fastcraft.Tweaker", TargetedMod.FASTCRAFT)
        .put("cofh.asm.LoadingPlugin", TargetedMod.COFHCORE)
        .build();
    public static final Set<TargetedMod> coreMods = new HashSet<>();

    private static void detectCoreMods(Set<String> loadedCoreMods) {
        MODS_BY_CLASS.forEach((key, value) -> {
            if (loadedCoreMods.contains(key))
                coreMods.add(value);
        });
    }

}
