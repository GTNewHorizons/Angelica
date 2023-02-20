package com.gtnewhorizons.angelica.loading;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({ "com.gtnewhorizons.angelica.loading.",
        "com.gtnewhorizons.angelica.transform." })
@IFMLLoadingPlugin.SortingIndex(1100)
public class AngelicaTweaker implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "com.gtnewhorizons.angelica.transform.AClassTransformer" };
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
}
