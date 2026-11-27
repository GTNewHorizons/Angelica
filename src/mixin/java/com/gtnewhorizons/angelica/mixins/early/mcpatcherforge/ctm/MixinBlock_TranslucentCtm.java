package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.ctm;

import net.minecraft.block.Block;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.prupe.mcpatcher.ctm.CTMUtils;

@Mixin(Block.class)
public abstract class MixinBlock_TranslucentCtm {
    @ModifyReturnValue(method = "canRenderInPass(I)Z", at = @At("RETURN"), remap = false)
    private boolean angelica$ctmTranslucentPass(boolean original, int pass) {
        return CTMUtils.bBlockNeedsPass((Block) (Object) this, pass) || original;
    }
}
