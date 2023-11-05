package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;

import net.minecraft.block.Block;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Block.class)
public class MixinBlock {

    @Shadow public Block.SoundType stepSound;

    @Redirect(method = "<init>", at = @At(opcode = Opcodes.PUTFIELD, value = "FIELD", target = "Lnet/minecraft/block/Block;stepSound:Lnet/minecraft/block/Block$SoundType;", ordinal = 0))
    private void onConstruct(Block block, Block.SoundType sound) {
        stepSound = sound;
    }
}
