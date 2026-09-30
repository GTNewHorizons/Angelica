package com.gtnewhorizons.angelica.mixins.late.client.malisisdoors;

import static net.malisis.doors.MalisisDoors.Blocks.camoFenceGate;

import com.gtnewhorizons.angelica.rendering.BlockMaterialAttribute;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.api.v0.IrisApi;
import net.malisis.core.renderer.MalisisRenderer;
import net.malisis.core.util.BlockState;
import net.malisis.doors.door.tileentity.BigDoorTileEntity;
import net.malisis.doors.door.tileentity.DoorTileEntity;
import net.malisis.doors.door.tileentity.FenceGateTileEntity;
import net.malisis.doors.door.tileentity.ForcefieldTileEntity;
import net.malisis.doors.entity.VanishingTileEntity;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Doors, gates, trapdoors and vanishing frames use TESRs to animate. We need to apply the correct gbuffer program to each block.
 */
@Mixin(value = MalisisRenderer.class, remap = false)
public abstract class MixinMalisisRenderer {

    @Unique
    private boolean angelica$drawingBlockAsTerrain;

    @WrapMethod(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", remap = true)
    private void angelica$drawBlocksAsTerrain(TileEntity tileEntity, double x, double y, double z, float partialTicks, Operation<Void> original) {
        if (!angelica$isTerrainBlock(tileEntity) || !IrisApi.getInstance().isShaderPackInUse()) {
            original.call(tileEntity, x, y, z, partialTicks);
            return;
        }

        Block materialBlock = tileEntity.getBlockType();
        int materialMetadata = tileEntity.getBlockMetadata();
        if (materialBlock == camoFenceGate && tileEntity instanceof FenceGateTileEntity gate && gate.getWorldObj() != null) {
            final AccessorFenceGateTileEntity access = (AccessorFenceGateTileEntity) gate;
            BlockState camo = access.angelica$getCamoState();
            if (camo == null) {
                gate.updateAll();
                camo = access.angelica$getCamoState();
            }
            if (camo != null && camo.getBlock() != materialBlock) {
                materialBlock = camo.getBlock();
                materialMetadata = camo.getMetadata();
            }
        }

        final CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        state.pushCurrentBlockEntity();
        state.setCurrentBlockEntity(0);
        final WorldRenderingPhase phase = tileEntity instanceof BigDoorTileEntity
            ? WorldRenderingPhase.TERRAIN_CUTOUT
            : BlockMaterialAttribute.renderingPhase(materialBlock, false);
        GbufferPrograms.pushOverridePhase(phase);
        BlockMaterialAttribute.set(materialBlock, materialMetadata);
        final boolean wasDrawingBlockAsTerrain = angelica$drawingBlockAsTerrain;
        angelica$drawingBlockAsTerrain = true;
        try {
            original.call(tileEntity, x, y, z, partialTicks);
        } finally {
            angelica$drawingBlockAsTerrain = wasDrawingBlockAsTerrain;
            BlockMaterialAttribute.reset();
            GbufferPrograms.popOverridePhase();
            state.popCurrentBlockEntity();
        }
    }

    @ModifyExpressionValue(method = "calcVertexColor", at = @At(value = "INVOKE", target = "Ljava/lang/Float;floatValue()F"))
    private float angelica$terrainFaceShade(float colorFactor) {
        return angelica$drawingBlockAsTerrain && BlockRenderingSettings.INSTANCE.shouldDisableDirectionalShading() ? 1.0F : colorFactor;
    }

    @Unique
    private static boolean angelica$isTerrainBlock(TileEntity tileEntity) {
        return (tileEntity instanceof DoorTileEntity && !(tileEntity instanceof ForcefieldTileEntity))
            || tileEntity instanceof BigDoorTileEntity
            || tileEntity instanceof VanishingTileEntity;
    }
}
