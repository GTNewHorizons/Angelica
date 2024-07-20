package com.gtnewhorizons.angelica.loading;

import com.google.common.collect.ImmutableMap;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.mixins.Mixins;
import com.gtnewhorizons.angelica.mixins.TargetedMod;
import com.gtnewhorizons.angelica.transform.compat.CompatASMTransformers;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions("com.gtnewhorizons.angelica.transform.RedirectorTransformer")
@IFMLLoadingPlugin.SortingIndex(Integer.MAX_VALUE - 5)
public class AngelicaTweaker implements IFMLLoadingPlugin, IEarlyMixinLoader {

    public static final Logger LOGGER = LogManager.getLogger("Angelica");

    private String[] transformerClasses;

    static {
        try {
            // Angelica Config
            ConfigurationManager.registerConfig(AngelicaConfig.class);
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
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
        List<String> mixinTweakClasses = GlobalProperties.get(MixinServiceLaunchWrapper.BLACKBOARD_KEY_TWEAKCLASSES);
        if (mixinTweakClasses != null) {
            mixinTweakClasses.add(MixinCompatHackTweaker.class.getName());
        }

        // Return any others here
        if (transformerClasses == null) {

            //Mod Compat transformers
            List<String> transformers = new ArrayList<String>();
            transformers.addAll(CompatASMTransformers.getTransformers());

            //NotFine transformers
            Namer.initNames();
            transformers.addAll(Arrays.asList(AsmTransformers.getTransformers()));

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
        // no-op
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
        // TODO: Sodium
//        mixins.addAll(getNotFineMixins(loadedCoreMods));
//        mixins.addAll(getArchaicMixins(loadedCoreMods));
        return Mixins.getEarlyMixins(loadedCoreMods);
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
