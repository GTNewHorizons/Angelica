package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.mixins.interfaces.IChunkTileEntityMapHolder;
import com.gtnewhorizons.angelica.utils.ConcurrentTileEntityMap;

@Pseudo
@Mixin(targets = "com.cardinalstar.cubicchunks.world.cube.Cube", remap = false)
public abstract class MixinCubicChunksCube {

    @Final
    @Shadow(remap = false)
    private Chunk column;

    @Surround(method = "setBlockTileEntityInChunk")
    private void angelica$lockTileEntityMutation(int x, int y, int z, TileEntity tileEntity) {
        @Surround.Carry
        ConcurrentTileEntityMap tileEntities = ((IChunkTileEntityMapHolder) column).angelica$getConcurrentTEMap();
        tileEntities.writeLock();
    }

    @Surround.Finally
    private void angelica$unlockTileEntityMutation(@Surround.Carry ConcurrentTileEntityMap tileEntities) {
        tileEntities.writeUnlock();
    }
}
