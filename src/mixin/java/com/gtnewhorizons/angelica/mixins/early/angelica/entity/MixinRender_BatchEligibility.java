package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.mixins.interfaces.IBatchEligibility;
import net.minecraft.client.renderer.entity.Render;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Render.class)
public abstract class MixinRender_BatchEligibility implements IBatchEligibility {

    @Unique
    private byte angelica$batchState;

    @Override
    public byte angelica$batchState() {
        return angelica$batchState;
    }

    @Override
    public void angelica$setBatchState(byte state) {
        angelica$batchState = state;
    }
}
