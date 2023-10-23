package com.gtnewhorizons.angelica.mixins.early.renderer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFlowerPot;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.item.Item;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.gtnewhorizons.angelica.client.Shaders;

@Mixin(RenderBlocks.class)
public class MixinRenderBlocks {

    // Block
    @Inject(method = "renderBlockByRenderType(Lnet/minecraft/block/Block;III)Z", at = @At("HEAD"))
    private void angelica$pushEntityBlock(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        Shaders.pushEntity((RenderBlocks) ((Object) this), block, x, y, z);
    }

    @Inject(method = "renderBlockByRenderType(Lnet/minecraft/block/Block;III)Z", at = @At("RETURN"))
    private void angelica$popEntityBlock(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        Shaders.popEntity();
    }

    // BlockFlowerPot
    @Inject(method = "renderBlockFlowerpot(Lnet/minecraft/block/BlockFlowerPot;III)Z", at = @At("HEAD"))
    private void angelica$pushEntityBlockFlowerPot(BlockFlowerPot blockFlowerPot, int x, int y, int z,
            CallbackInfoReturnable<Boolean> cir) {
        Shaders.pushEntity((RenderBlocks) ((Object) this), blockFlowerPot, x, y, z);
    }

    @Inject(method = "renderBlockFlowerpot(Lnet/minecraft/block/BlockFlowerPot;III)Z", at = @At("RETURN"))
    private void angelica$popEntityBlockFlowerPot(BlockFlowerPot blockFlowerPot, int x, int y, int z,
            CallbackInfoReturnable<Boolean> cir) {
        Shaders.popEntity();
    }

    @WrapOperation(
            method = "renderBlockFlowerpot(Lnet/minecraft/block/BlockFlowerPot;III)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;getBlockFromItem(Lnet/minecraft/item/Item;)Lnet/minecraft/block/Block;"))
    private Block angelica$pushEntityFlowerInFlowerPot(Item itemIn, Operation<Block> original) {
        final Block block = original.call(itemIn);
        Shaders.pushEntity(block);
        return block;
    }

    @Inject(
            method = "renderBlockFlowerpot(Lnet/minecraft/block/BlockFlowerPot;III)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Tessellator;addTranslation(FFF)V",
                    shift = At.Shift.AFTER,
                    ordinal = 1))
    private void angelica$popEntityFlowerInFlowerPot(BlockFlowerPot blockFlowerPot, int x, int y, int z,
            CallbackInfoReturnable<Boolean> cir) {
        Shaders.popEntity();
    }
}
