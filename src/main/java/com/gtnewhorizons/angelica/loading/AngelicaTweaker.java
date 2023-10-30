package com.gtnewhorizons.angelica.loading;

import com.google.common.collect.ImmutableMap;
import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import com.gtnewhorizons.angelica.mixins.ArchaicMixins;
import com.gtnewhorizons.angelica.mixins.Mixins;
import com.gtnewhorizons.angelica.mixins.TargetedMod;
import com.gtnewhorizons.angelica.transform.IrisTransformer;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.embeddedt.archaicfix.config.ConfigException;
import org.embeddedt.archaicfix.config.ConfigurationManager;
import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({
    "org.lwjglx", "org.lwjgl", "org.lwjgl.input", "org.lwjglx.input", "org.lwjglx.debug", "me.eigenraven.lwjgl3ify", "com.gtnewhorizons.angelica.transform.IrisTransformer",
})
@IFMLLoadingPlugin.SortingIndex(1100)
public class AngelicaTweaker implements IFMLLoadingPlugin, IEarlyMixinLoader {

    public static final Logger LOGGER = LogManager.getLogger("Angelica");

    static {
        try {
            // ArchaicFix Config
            ConfigurationManager.registerConfig(ArchaicConfig.class);
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            loggerConfig.setLevel(Level.DEBUG);
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
        return new String[] { IrisTransformer.class.getName() };
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
        final List<String> mixins = new ArrayList<>();
        final List<String> notLoading = new ArrayList<>();
        for (Mixins mixin : Mixins.values()) {
            if (mixin.phase == Mixins.Phase.EARLY) {
                if (mixin.shouldLoad(loadedCoreMods, Collections.emptySet())) {
                    mixins.addAll(mixin.mixinClasses);
                } else {
                    notLoading.addAll(mixin.mixinClasses);
                }
            }
        }
        LOGGER.info("Not loading the following EARLY mixins: {}", notLoading);
        mixins.addAll(getNotFineMixins(loadedCoreMods));
        mixins.addAll(getArchaicMixins(loadedCoreMods));
        return mixins;
    }

    private List<String> getNotFineMixins(Set<String> loadedCoreMods) {
        final List<String> mixins = new ArrayList<>();
        mixins.add("notfine.clouds.MixinEntityRenderer");
        mixins.add("notfine.clouds.MixinGameSettings");
        mixins.add("notfine.clouds.MixinRenderGlobal");
        mixins.add("notfine.clouds.MixinWorldType");

        mixins.add("notfine.glint.MixinItemRenderer");
        mixins.add("notfine.glint.MixinRenderBiped");
        mixins.add("notfine.glint.MixinRenderItem");
        mixins.add("notfine.glint.MixinRenderPlayer");

        mixins.add("notfine.gui.MixinGuiSlot");

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

        mixins.add("notfine.settings.MixinGameSettings");
        mixins.add("notfine.renderer.MixinRenderGlobal");
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

    public List<String> getArchaicMixins(Set<String> loadedCoreMods) {
        List<String> mixins = new ArrayList<>();
        detectCoreMods(loadedCoreMods);
        LOGGER.info("Detected coremods: [" + coreMods.stream().map(TargetedMod::name).collect(Collectors.joining(", ")) + "]");
        for(ArchaicMixins mixin : ArchaicMixins.values()) {
            if(mixin.getPhase() == ArchaicMixins.Phase.EARLY && mixin.shouldLoadSide() && mixin.getFilter().test(coreMods)) {
                mixins.addAll(mixin.getMixins());
            }
        }
        return mixins;
    }


}
