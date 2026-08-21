package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import com.gtnewhorizons.angelica.mixins.interfaces.IChunkTileEntityMapHolder;
import com.gtnewhorizons.angelica.utils.ConcurrentTileEntityMap;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Pseudo
@Mixin(targets = "com.cardinalstar.cubicchunks.world.cube.Cube", remap = false)
public abstract class MixinCubicChunksCube {

    @Final
    @Shadow(remap = false)
    private Chunk column;

    @WrapMethod(method = "setBlockTileEntityInChunk")
    private void angelica$lockTileEntityMutation(int x, int y, int z, TileEntity tileEntity, Operation<Void> original) {
        ConcurrentTileEntityMap tileEntities = ((IChunkTileEntityMapHolder) column).angelica$getConcurrentTEMap();
        tileEntities.withWriteLock(() -> original.call(x, y, z, tileEntity));
    }
}
