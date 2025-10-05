package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.mixins.interfaces.IGameSettingsExt;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GameSettings.class)
public class MixinGameSettings implements IGameSettingsExt {
    @Unique
    private boolean angelica$showFpsGraph = false;

    @Override
    public boolean angelica$showFpsGraph() {
        return this.angelica$showFpsGraph;
    }

    @Override
    public void angelica$setShowFpsGraph(boolean renderFpsGraph) {
        this.angelica$showFpsGraph = renderFpsGraph;
    }
}
