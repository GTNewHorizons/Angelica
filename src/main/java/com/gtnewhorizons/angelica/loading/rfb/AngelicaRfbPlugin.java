package com.gtnewhorizons.angelica.loading.rfb;

import com.gtnewhorizons.angelica.loading.rfb.transformers.RFBAngelicaRedirector;
import com.gtnewhorizons.retrofuturabootstrap.api.PluginContext;
import com.gtnewhorizons.retrofuturabootstrap.api.RetroFuturaBootstrap;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbPlugin;
import net.minecraft.launchwrapper.Launch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AngelicaRfbPlugin implements RfbPlugin {

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
            new RFBAngelicaRedirector()
        };
    }
}
