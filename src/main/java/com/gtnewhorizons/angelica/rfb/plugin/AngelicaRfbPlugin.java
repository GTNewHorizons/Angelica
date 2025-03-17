package com.gtnewhorizons.angelica.rfb.plugin;

import com.gtnewhorizons.angelica.rfb.transformers.GLRedirectorRfbTransformer;
import com.gtnewhorizons.angelica.rfb.transformers.RedirectorTransformerWrapper;
import com.gtnewhorizons.retrofuturabootstrap.SharedConfig;
import com.gtnewhorizons.retrofuturabootstrap.api.PluginContext;
import com.gtnewhorizons.retrofuturabootstrap.api.RetroFuturaBootstrap;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbPlugin;
import net.minecraft.launchwrapper.Launch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Angelica RFB Plugin to register transformers
 */
public class AngelicaRfbPlugin implements RfbPlugin {

    public static final boolean VERBOSE = Boolean.parseBoolean(System.getProperty("angelica.verbose", "false"));

    @Override
    public void onConstruction(@NotNull PluginContext ctx) {
        Launch.blackboard.put("angelica.rfbPluginLoaded", Boolean.TRUE);
    }

    @Override
    public @NotNull RfbClassTransformer @Nullable [] makeTransformers() {
        final boolean isServer = (null == RetroFuturaBootstrap.API.launchClassLoader().findClassMetadata("net.minecraft.client.main.Main"));
        if (isServer) {
            return null;
        }

        return new RfbClassTransformer[] {
              new RedirectorTransformerWrapper()
            , new GLRedirectorRfbTransformer()
        };
    }
}
