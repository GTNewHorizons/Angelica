package com.gtnewhorizons.angelica.loading;

import com.google.common.collect.ImmutableMap;
import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.ConfigException;
import com.gtnewhorizons.angelica.config.ConfigurationManager;
import com.gtnewhorizons.angelica.mixins.Mixins;
import com.gtnewhorizons.angelica.mixins.TargetedMod;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import mist475.mcpatcherforge.asm.AsmTransformers;
import mist475.mcpatcherforge.asm.mappings.Namer;
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
import java.util.Collections;
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
            Namer.initNames();
            transformerClasses = AsmTransformers.getTransformers();
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

    private List<String> getNotFineMixins(Set<String> loadedCoreMods) {
        if(FMLLaunchHandler.side().isServer())
            return Collections.emptyList();

        final List<String> mixins = new ArrayList<>();
        mixins.add("notfine.clouds.MixinEntityRenderer");
        mixins.add("notfine.clouds.MixinGameSettings");
        mixins.add("notfine.clouds.MixinRenderGlobal");
        mixins.add("notfine.clouds.MixinWorldType");

        mixins.add("notfine.leaves.MixinBlockLeaves");
        mixins.add("notfine.leaves.MixinBlockLeavesBase");

        mixins.add("notfine.particles.MixinBlockEnchantmentTable");
        mixins.add("notfine.particles.MixinEffectRenderer");
        mixins.add("notfine.particles.MixinWorldClient");
        mixins.add("notfine.particles.MixinWorldProvider");

        mixins.add("notfine.toggle.MixinGuiIngame");
        mixins.add("notfine.toggle.MixinEntityRenderer");
        mixins.add("notfine.toggle.MixinRender");
        mixins.add("notfine.toggle.MixinRenderItem");
        return mixins;
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
