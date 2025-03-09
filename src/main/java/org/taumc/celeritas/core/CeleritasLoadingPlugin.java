package org.taumc.celeritas.core;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

@IFMLLoadingPlugin.Name("Celeritas")
@IFMLLoadingPlugin.MCVersion("1.7.10")
public class CeleritasLoadingPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {
    @Override
    public @Nullable String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public @Nullable String getModContainerClass() {
        return null;
    }

    @Override
    public @Nullable String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> map) {

    }

    @Override
    public @Nullable String getAccessTransformerClass() {
        return null;
    }

    @Override
    public String getMixinConfig() {
        return "mixins.celeritas.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return List.of();
    }
}
