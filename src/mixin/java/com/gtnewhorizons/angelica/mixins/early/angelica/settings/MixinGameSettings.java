package com.gtnewhorizons.angelica.mixins.early.angelica.settings;

import com.gtnewhorizons.angelica.mixins.interfaces.IGameSettingsExt;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

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

    /**
     * @author embeddedt
     * @reason Sodium Renderer supports up to 32 chunks
     */
    @ModifyConstant(method = "<init>(Lnet/minecraft/client/Minecraft;Ljava/io/File;)V", constant = @Constant(floatValue = 16.0f))
    private float increaseMaxDistance(float old) {
        return 128;
    }
}
