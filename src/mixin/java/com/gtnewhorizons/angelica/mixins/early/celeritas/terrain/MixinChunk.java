package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import java.util.Map;

import com.gtnewhorizons.angelica.mixins.interfaces.IChunkTileEntityMapHolder;
import com.gtnewhorizons.angelica.rendering.RenderThreadContext;
import com.gtnewhorizons.angelica.utils.ConcurrentTileEntityMap;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Chunk.class)
public abstract class MixinChunk implements IChunkTileEntityMapHolder {

    @Shadow public Map<ChunkPosition, TileEntity> chunkTileEntityMap = new ConcurrentTileEntityMap();
    @Shadow public World worldObj;
    @Shadow private ExtendedBlockStorage[] storageArrays;
    @Final @Shadow public int xPosition;
    @Final @Shadow public int zPosition;

    @Shadow public abstract void addTileEntity(TileEntity te);

    @Override
    public ConcurrentTileEntityMap angelica$getConcurrentTEMap() {
        return (ConcurrentTileEntityMap) this.chunkTileEntityMap;
    }

    @Redirect(method = "getTileEntityUnsafe", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), remap = false)
    private Object angelica$redirectGetTEUnsafe(Map<ChunkPosition, TileEntity> map, Object key) {
        if (RenderThreadContext.hasWorldSlice()) {
            final ChunkPosition pos = (ChunkPosition) key;
            final TileEntity te = RenderThreadContext.getSnapshotTE((this.xPosition << 4) + pos.chunkPosX, pos.chunkPosY, (this.zPosition << 4) + pos.chunkPosZ);
            if (te != null) return te;
        }
        return map.get(key);
    }

    @Redirect(method = "func_150806_e", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object angelica$redirectGetTEFunc(Map<ChunkPosition, TileEntity> map, Object key) {
        if (RenderThreadContext.hasWorldSlice()) {
            final ChunkPosition pos = (ChunkPosition) key;
            final TileEntity te = RenderThreadContext.getSnapshotTE((this.xPosition << 4) + pos.chunkPosX, pos.chunkPosY, (this.zPosition << 4) + pos.chunkPosZ);
            if (te != null) return te;
        }
        return map.get(key);
    }

    @Redirect(method = "func_150806_e", at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object angelica$deferRemoveFunc150806e(Map<ChunkPosition, TileEntity> map, Object key) {
        return angelica$deferRemoveImpl(map, key);
    }

    @Redirect(method = "getTileEntityUnsafe", at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"), remap = false)
    private Object angelica$deferRemoveGetTEUnsafe(Map<ChunkPosition, TileEntity> map, Object key) {
        return angelica$deferRemoveImpl(map, key);
    }

    @Redirect(method = "removeInvalidTileEntity", at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"), remap = false)
    private Object angelica$deferRemoveInvalidTE(Map<ChunkPosition, TileEntity> map, Object key) {
        return angelica$deferRemoveImpl(map, key);
    }

    private Object angelica$deferRemoveImpl(Map<ChunkPosition, TileEntity> map, Object key) {
        if (!RenderThreadContext.hasWorldSlice()) return map.remove(key);
        ((ConcurrentTileEntityMap) map).queueInvalidation((ChunkPosition) key);
        return null;
    }

    @Inject(method = "fillChunk", at = @At("RETURN"))
    private void angelica$createTileEntities(byte[] data, int primaryBitMask, int addBitMask, boolean groundUp, CallbackInfo ci) {
        ((ConcurrentTileEntityMap) this.chunkTileEntityMap).withWriteLock(() -> {
            final boolean hasExistingTEs = !this.chunkTileEntityMap.isEmpty();

            for (int sectionY = 0; sectionY < this.storageArrays.length; sectionY++) {
                if ((primaryBitMask & (1 << sectionY)) == 0) continue;

                final ExtendedBlockStorage section = this.storageArrays[sectionY];
                if (section == null) continue;

                final int baseY = sectionY << 4;
                for (int y = 0; y < 16; y++) {
                    final int worldY = baseY + y;
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            final Block block = section.getBlockByExtId(x, y, z);
                            if (block == null) continue;
                            final int meta = section.getExtBlockMetadata(x, y, z);
                            if (block.hasTileEntity(meta)) {
                                if (hasExistingTEs) {
                                    final ChunkPosition pos = new ChunkPosition(x, worldY, z);
                                    final TileEntity existing = this.chunkTileEntityMap.get(pos);
                                    if (existing != null && !existing.isInvalid()) continue;
                                }
                                final TileEntity te = block.createTileEntity(this.worldObj, meta);
                                if (te != null) {
                                    te.xCoord = (this.xPosition << 4) + x;
                                    te.yCoord = worldY;
                                    te.zCoord = (this.zPosition << 4) + z;
                                    this.addTileEntity(te);
                                }
                            }
                        }
                    }
                }
            }
        });
    }
}
