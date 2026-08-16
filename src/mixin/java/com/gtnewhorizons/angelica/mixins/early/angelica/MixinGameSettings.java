package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.mixins.interfaces.IGameSettingsExt;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameSettings.class)
public class MixinGameSettings implements IGameSettingsExt {
    @Unique
    private boolean angelica$showFpsGraph = false;

    @Redirect(method = "setOptionValue", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setVSyncEnabled(Z)V", remap = false))
    private void angelica$redirectVSync(boolean sync) {
        ClientProxy.toggleVSync(sync);
    }

    @Override
    public boolean angelica$showFpsGraph() {
        return this.angelica$showFpsGraph;
    }

    @Override
    public void angelica$setShowFpsGraph(boolean renderFpsGraph) {
        this.angelica$showFpsGraph = renderFpsGraph;
    }
}
