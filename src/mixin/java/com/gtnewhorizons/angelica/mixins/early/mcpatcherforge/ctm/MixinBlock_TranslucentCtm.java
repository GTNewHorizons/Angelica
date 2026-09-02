package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.ctm;

import net.minecraft.block.Block;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.prupe.mcpatcher.ctm.CTMUtils;

@Mixin(Block.class)
public abstract class MixinBlock_TranslucentCtm {

    /**
     * Routes a block carrying a renderPass=translucent CTM override onto pass 1 (translucent) exclusively,
     * replacing whatever vanilla/the block itself would otherwise answer -- see
     * {@link CTMUtils#hasTranslucentOverride(Block)}. This is a replacement, not a widening: a flagged block
     * renders exactly once, through the translucent Material's buffer, so its texture's real alpha actually gets
     * blended instead of the SOLID pass's alpha-blind treatment. Untouched for every other block.
     * <p>
     * Only takes effect for blocks that don't override canRenderInPass themselves (e.g. vanilla blocks) --
     * virtual dispatch means a subclass override (GT's machine/casing blocks, for instance) never reaches this
     * mixin's injection on the base Block implementation at all.
     */
    @ModifyReturnValue(method = "canRenderInPass(I)Z", at = @At("RETURN"), remap = false)
    private boolean angelica$ctmTranslucentPass(boolean original, int pass) {
        if (CTMUtils.hasTranslucentOverride((Block) (Object) this)) {
            return pass == 1;
        }
        return original;
    }
}
