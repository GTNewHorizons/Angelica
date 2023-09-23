package org.embeddedt.archaicfix;

import com.google.common.collect.ImmutableMap;
import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.archaicfix.asm.Mixin;
import org.embeddedt.archaicfix.asm.TargetedMod;
import org.embeddedt.archaicfix.asm.transformer.VampirismTransformer;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.embeddedt.archaicfix.config.ConfigException;
import org.embeddedt.archaicfix.config.ConfigurationManager;
import org.embeddedt.archaicfix.helpers.LetsEncryptHelper;

import java.util.*;
import java.util.stream.Collectors;

@IFMLLoadingPlugin.Name("ArchaicCore")
@IFMLLoadingPlugin.MCVersion("1.7.10")
public class ArchaicCore implements IFMLLoadingPlugin, IEarlyMixinLoader {
    public static final Logger LOGGER = LogManager.getLogger("ArchaicCore");
    static {
        VampirismTransformer.init();
        try {
            ConfigurationManager.registerConfig(ArchaicConfig.class);
        } catch(ConfigException e) {
            throw new RuntimeException(e);
        }
        LetsEncryptHelper.replaceSSLContext();
    }
    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
            "org.embeddedt.archaicfix.asm.ArchaicTransformer"
        };
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

    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public String getMixinConfig() {
        return "mixins.archaicfix.early.json";
    }

    public static Set<TargetedMod> coreMods = new HashSet<>();

    private static final ImmutableMap<String, TargetedMod> MODS_BY_CLASS = ImmutableMap.<String, TargetedMod>builder()
            .put("optifine.OptiFineForgeTweaker", TargetedMod.OPTIFINE)
            .put("fastcraft.Tweaker", TargetedMod.FASTCRAFT)
            .put("cofh.asm.LoadingPlugin", TargetedMod.COFHCORE)
            .build();

    private static void detectCoreMods(Set<String> loadedCoreMods) {
        MODS_BY_CLASS.forEach((key, value) -> {
            if (loadedCoreMods.contains(key))
                coreMods.add(value);
        });
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        List<String> mixins = new ArrayList<>();
        detectCoreMods(loadedCoreMods);
        LOGGER.info("Detected coremods: [" + coreMods.stream().map(TargetedMod::name).collect(Collectors.joining(", ")) + "]");
        for(Mixin mixin : Mixin.values()) {
            if(mixin.getPhase() == Mixin.Phase.EARLY && mixin.shouldLoadSide() && mixin.getFilter().test(coreMods)) {
                mixins.add(mixin.getMixin());
            }
        }
        return mixins;
    }
}
