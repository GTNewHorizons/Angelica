package com.gtnewhorizons.umbra.loading;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Umbra coremod entry point. Detects Angelica and delegates to client tweaker.
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
public class UmbraTweaker implements IFMLLoadingPlugin, IEarlyMixinLoader {

    private final IFMLLoadingPlugin loadingPlugin;
    private final IEarlyMixinLoader mixinLoader;

    public UmbraTweaker() {
        if (FMLLaunchHandler.side().isClient()) {
            final UmbraClientTweaker clientTweaker = new UmbraClientTweaker();
            loadingPlugin = clientTweaker;
            mixinLoader = clientTweaker;
        } else {
            loadingPlugin = null;
            mixinLoader = null;
            FMLRelaunchLog.info("[Umbra] Server side detected, skipping client initialization");
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return loadingPlugin != null ? loadingPlugin.getASMTransformerClass() : new String[0];
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
        if (loadingPlugin != null) {
            loadingPlugin.injectData(data);
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public String getMixinConfig() {
        if (mixinLoader != null) {
            return mixinLoader.getMixinConfig();
        }
        return null;
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        if (mixinLoader != null) {
            return mixinLoader.getMixins(loadedCoreMods);
        }
        return Collections.emptyList();
    }
}
