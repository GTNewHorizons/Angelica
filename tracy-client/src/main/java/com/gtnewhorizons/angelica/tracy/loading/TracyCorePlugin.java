package com.gtnewhorizons.angelica.tracy.loading;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/// Empty coremod to force early loading
@IFMLLoadingPlugin.MCVersion("1.7.10")
public class TracyCorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return null;
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
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
