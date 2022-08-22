package com.gtnewhorizons.angelica.loading;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;

@IFMLLoadingPlugin.TransformerExclusions({
    "com.gtnewhorizons.angelica",
    "com.gtnewhorizons.angelica.loading",
    "com.gtnewhorizons.angelica.transform"
})
@IFMLLoadingPlugin.MCVersion("1.7.10")
public class SMCTweaker implements IFMLLoadingPlugin {
    public SMCTweaker() {}

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {"com.gtnewhorizons.angelica.transform.SMCClassTransformer"};
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
    public void injectData(Map<String, Object> map) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
