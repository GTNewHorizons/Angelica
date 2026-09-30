package com.gtnewhorizons.angelica.mixins.late.client.malisisdoors;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.rendering.BlockMaterialAttribute;
import com.gtnewhorizons.angelica.rendering.StateAwareTessellator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.api.v0.IrisApi;
import net.malisis.core.renderer.MalisisRenderer;
import net.malisis.core.renderer.RenderType;
import net.malisis.doors.renderer.VanishingBlockRenderer;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Assigns the copied block's shader material only to geometry using its texture. */
@Mixin(value = VanishingBlockRenderer.class, remap = false)
public abstract class MixinVanishingBlockRenderer extends MalisisRenderer {

    @WrapOperation(method = {"render", "renderVanishingTileEntity"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/RenderBlocks;renderBlockByRenderType(Lnet/minecraft/block/Block;III)Z", remap = true))
    private boolean angelica$copiedBlockMaterial(RenderBlocks renderer, Block copiedBlock, int x, int y, int z,
                                                Operation<Boolean> original) {
        if (!IrisApi.getInstance().isShaderPackInUse() || renderer.hasOverrideBlockTexture()) {
            return original.call(renderer, copiedBlock, x, y, z);
        }

        final int copiedMetadata = renderer.blockAccess.getBlockMetadata(x, y, z);
        if (renderType == RenderType.ISBRH_WORLD) {
            final StateAwareTessellator tessellator = (StateAwareTessellator) TessellatorManager.get();
            final short previousId = tessellator.angelica$getShaderOverrideBlockId();
            final int copiedId = BlockMaterialAttribute.blockMaterialId(copiedBlock, copiedMetadata);
            tessellator.angelica$setShaderOverrideBlockId(copiedId == -1
                ? StateAwareTessellator.UNMAPPED_SHADER_BLOCK_ID : (short) copiedId);
            try {
                return original.call(renderer, copiedBlock, x, y, z);
            } finally {
                tessellator.angelica$setShaderOverrideBlockId(previousId);
            }
        }

        if (renderType != RenderType.TESR_WORLD) {
            return original.call(renderer, copiedBlock, x, y, z);
        }

        final Block frameBlock = block;
        final int frameMetadata = blockMetadata;
        next();
        GbufferPrograms.pushOverridePhase(BlockMaterialAttribute.renderingPhase(copiedBlock, false));
        BlockMaterialAttribute.set(copiedBlock, copiedMetadata);
        try {
            return original.call(renderer, copiedBlock, x, y, z);
        } finally {
            try {
                next();
            } finally {
                BlockMaterialAttribute.set(frameBlock, frameMetadata);
                GbufferPrograms.popOverridePhase();
            }
        }
    }

    @WrapOperation(method = {"renderVanishingTileEntity", "renderCopiedTileEntity"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/tileentity/TileEntityRendererDispatcher;renderTileEntity(Lnet/minecraft/tileentity/TileEntity;F)V", remap = true))
    private void angelica$copiedTileEntityPhase(TileEntityRendererDispatcher dispatcher, TileEntity copiedTileEntity,
                                               float partialTicks, Operation<Void> original) {
        if (!IrisApi.getInstance().isShaderPackInUse()) {
            original.call(dispatcher, copiedTileEntity, partialTicks);
            return;
        }

        // Malisis has already flushed and cleaned the frame before dispatching the copied tile entity.
        BlockMaterialAttribute.reset();
        GbufferPrograms.pushOverridePhase(WorldRenderingPhase.BLOCK_ENTITIES);
        try {
            original.call(dispatcher, copiedTileEntity, partialTicks);
        } finally {
            GbufferPrograms.popOverridePhase();
        }
    }
}
