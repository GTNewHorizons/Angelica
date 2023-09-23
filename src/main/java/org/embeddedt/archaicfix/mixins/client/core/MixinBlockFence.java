package org.embeddedt.archaicfix.mixins.client.core;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.material.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockFence.class)
public abstract class MixinBlockFence extends Block {
    protected MixinBlockFence(Material p_i45394_1_) {
        super(p_i45394_1_);
    }

    /**
     * Fix a smooth lighting glitch with fences against solid blocks.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void makeTransparent(String s, Material m, CallbackInfo ci) {
        this.canBlockGrass = true;
    }
}
