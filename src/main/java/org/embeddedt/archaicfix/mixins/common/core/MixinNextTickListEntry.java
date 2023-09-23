package org.embeddedt.archaicfix.mixins.common.core;

import net.minecraft.block.Block;
import net.minecraft.world.NextTickListEntry;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NextTickListEntry.class)
public class MixinNextTickListEntry {
    @Shadow public int xCoord;

    @Shadow public int yCoord;

    @Shadow public int zCoord;

    @Shadow @Final private Block field_151352_g;

    @Inject(method = "compareTo(Lnet/minecraft/world/NextTickListEntry;)I", at = @At("RETURN"))
    private void considerCoordinatesForEquality(NextTickListEntry other, CallbackInfoReturnable<Integer> cir) {
        if(ArchaicConfig.fixTickListSynchronization && cir.getReturnValue() == 0) {
            int myCoords = this.xCoord + this.yCoord + this.zCoord;
            int theirCoords = other.xCoord + other.yCoord + other.zCoord;
            int diff = theirCoords - myCoords;
            if(diff != 0)
                cir.setReturnValue(diff);
            else {
                Block b1 = other.func_151351_a();
                Block b2 = this.field_151352_g;
                if(b1 != b2)
                    cir.setReturnValue(Block.getIdFromBlock(b1) - Block.getIdFromBlock(b2));
            }
        }
    }
}
