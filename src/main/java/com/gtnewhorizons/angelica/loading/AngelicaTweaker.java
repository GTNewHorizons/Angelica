package com.gtnewhorizons.angelica.loading;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
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
@IFMLLoadingPlugin.MCVersion("1.7.10")
public class AngelicaTweaker implements IFMLLoadingPlugin, IEarlyMixinLoader {

    private final IFMLLoadingPlugin loadingPlugin;
    private final IEarlyMixinLoader mixinLoader;

    public AngelicaTweaker() {
        if (FMLLaunchHandler.side().isClient()) {
            final AngelicaClientTweaker clientTweaker = new AngelicaClientTweaker();
            loadingPlugin = clientTweaker;
            mixinLoader = clientTweaker;
        } else {
            loadingPlugin = null;
            mixinLoader = null;
            LogManager.getLogger("Angelica").info("Trying to get more FPS on your server :kekw:");
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        if (this.loadingPlugin != null) {
            return this.loadingPlugin.getASMTransformerClass();
        }
        return null;
    }

    @Override
    public String getModContainerClass() {
        if (this.loadingPlugin != null) {
            return this.loadingPlugin.getModContainerClass();
        }
        return null;
    }

    @Override
    public String getSetupClass() {
        if (this.loadingPlugin != null) {
            return this.loadingPlugin.getSetupClass();
        }
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        if (this.loadingPlugin != null) {
            this.loadingPlugin.injectData(data);
        }
    }

    @Override
    public String getAccessTransformerClass() {
        if (this.loadingPlugin != null) {
            return this.loadingPlugin.getAccessTransformerClass();
        }
        return null;
    }

    @Override
    public String getMixinConfig() {
        if (this.mixinLoader != null) {
            return this.mixinLoader.getMixinConfig();
        }
        return "mixins.angelica.server.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        if (this.mixinLoader != null) {
            return this.mixinLoader.getMixins(loadedCoreMods);
        }
        return Collections.emptyList();
    }
}
