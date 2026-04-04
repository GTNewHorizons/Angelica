package com.gtnewhorizons.umbra.loading.rfb;

import com.gtnewhorizons.umbra.loading.rfb.transformers.RFBUmbraRedirector;
import com.gtnewhorizons.umbra.loading.shared.AngelicaDetector;
import com.gtnewhorizons.retrofuturabootstrap.api.PluginContext;
import com.gtnewhorizons.retrofuturabootstrap.api.RetroFuturaBootstrap;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbPlugin;
import net.minecraft.launchwrapper.Launch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UmbraRfbPlugin implements RfbPlugin {

    @Override
    public void onConstruction(@NotNull PluginContext ctx) {
        Launch.blackboard.put("umbra.rfbPluginLoaded", Boolean.TRUE);
        Launch.classLoader.addClassLoaderExclusion("com.gtnewhorizons.angelica.glsm.redirect.");
    }

    @Override
    public @NotNull RfbClassTransformer @Nullable [] makeTransformers() {
        // Detect Angelica -- if present, don't register any transformers
        if (AngelicaDetector.isPresent()) {
            Launch.blackboard.put("umbra.disabled", Boolean.TRUE);
            return null;
        }

        final boolean isServer = (null == RetroFuturaBootstrap.API.launchClassLoader()
            .findClassMetadata("net.minecraft.client.main.Main"));
        if (isServer) {
            return null;
        }

        return new RfbClassTransformer[] {
            new RFBUmbraRedirector()
        };
    }
}
