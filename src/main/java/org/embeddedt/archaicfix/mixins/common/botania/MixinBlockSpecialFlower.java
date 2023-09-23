package org.embeddedt.archaicfix.mixins.common.botania;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import vazkii.botania.common.block.BlockSpecialFlower;
import vazkii.botania.common.block.tile.TileSpecialFlower;

@Mixin(BlockSpecialFlower.class)
public class MixinBlockSpecialFlower {
    @Redirect(method = "getLightValue", at = @At(value = "INVOKE", target = "Lvazkii/botania/common/block/tile/TileSpecialFlower;getLightValue()I", remap = false), remap = false)
    private int archaic$avoidNullCrash(TileSpecialFlower instance) {
        if(instance == null)
            return -1;
        else
            return instance.getLightValue();
    }
}
