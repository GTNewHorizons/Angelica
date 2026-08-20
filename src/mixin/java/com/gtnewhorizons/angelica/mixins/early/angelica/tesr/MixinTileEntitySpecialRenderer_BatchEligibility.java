package com.gtnewhorizons.angelica.mixins.early.angelica.tesr;

import com.gtnewhorizons.angelica.mixins.interfaces.IBatchEligibility;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(TileEntitySpecialRenderer.class)
public abstract class MixinTileEntitySpecialRenderer_BatchEligibility implements IBatchEligibility {

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
