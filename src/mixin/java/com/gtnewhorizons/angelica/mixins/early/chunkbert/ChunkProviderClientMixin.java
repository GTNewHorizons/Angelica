package com.gtnewhorizons.angelica.mixins.early.chunkbert;

import com.embeddedt.chunkbert.ChunkbertConfig;
import com.embeddedt.chunkbert.FakeChunkManager;
import com.embeddedt.chunkbert.FakeChunkStorage;
import com.embeddedt.chunkbert.ext.IChunkProviderClient;
import com.gtnewhorizons.angelica.compat.mojang.ChunkPos;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.LongHashMap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkStatus;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * Replaces Celeritas' ClientChunkManagerMixin when Chunkbert is enabled
 * @see com.gtnewhorizons.angelica.mixins.early.celeritas.core.terrain.ClientChunkManagerMixin
 */
@Mixin(ChunkProviderClient.class)
public class ChunkProviderClientMixin implements IChunkProviderClient {
    @Shadow private Chunk blankChunk;
    @Shadow private World worldObj;

    @Unique
    protected @Nullable FakeChunkManager chunkbert$ChunkManager = null;

    // Cache of chunk which was just unloaded so we can immediately
    // load it again without having to wait for the storage io worker.
    @Unique
    protected @Nullable NBTTagCompound chunkbert$ChunkReplacement;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bobbyInit(World worldIn, CallbackInfo ci) {
        if(ChunkbertConfig.enabled)
            chunkbert$ChunkManager = new FakeChunkManager((WorldClient)worldIn, (ChunkProviderClient) (Object) this);
    }

    @Override
    public @Nullable FakeChunkManager chunkbert$getChunkManager() {
        return chunkbert$ChunkManager;
    }

    @Inject(method = "provideChunk", at = @At("RETURN"), cancellable = true)
    private void bobbyGetChunk(int x, int z, CallbackInfoReturnable<Chunk> ci) {
        // Did we find a live chunk?
        if (ci.getReturnValue() != blankChunk) {
            return;
        }

        if (chunkbert$ChunkManager == null) {
            return;
        }

        // Otherwise, see if we've got one
        Chunk chunk = chunkbert$ChunkManager.getChunk(x, z);
        if (chunk != null) {
            ci.setReturnValue(chunk);
        }
    }

    @Inject(method = "loadChunk", at = @At("HEAD"))
    private void bobbyUnloadFakeChunk(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        if (chunkbert$ChunkManager == null) {
            return;
        }

        // This needs to be called unconditionally because even if there is no chunk loaded at the moment,
        // we might already have one queued which we need to cancel as otherwise it will overwrite the real one later.
        chunkbert$ChunkManager.unload(x, z, true);
    }

    @Inject(method = "loadChunk", at = @At("RETURN"))
    private void bobbyFakeChunkReplaced(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        // Only call the tracker if there was no fake chunk in its place previously
        if (chunkbert$ChunkManager.getChunk(x, z) == null) {
            ChunkTrackerHolder.get(this.worldObj).onChunkStatusAdded(x, z, ChunkStatus.FLAG_ALL);
        } else {
            // If we failed to load the chunk from the packet for whatever reason,
            // and if there was a fake chunk in its place previously,
            // we need to notify the listener that the chunk has indeed been unloaded.
            if (worldObj.getChunkProvider() instanceof ChunkProviderClient provider) {
                if (provider.chunkMapping.getValueByKey(ChunkPos.toLong(x, z)) == null) {
                    ChunkTrackerHolder.get(this.worldObj).onChunkStatusRemoved(x, z, ChunkStatus.FLAG_ALL);
                }
            }
        }
    }

    @Inject(method = "unloadChunk", at = @At("HEAD"))
    private void bobbySaveChunk(int chunkX, int chunkZ, CallbackInfo ci) {
        if (chunkbert$ChunkManager == null) {
            return;
        }

        Chunk chunk = null;
        if (worldObj.getChunkProvider() instanceof ChunkProviderClient provider) {
            chunk = (Chunk) provider.chunkMapping.getValueByKey(ChunkPos.toLong(chunkX, chunkZ));
        }

        if (chunk == null) {
            return;
        }

        FakeChunkStorage storage = chunkbert$ChunkManager.getStorage();
        NBTTagCompound tag = storage.serialize(chunk);
        storage.save(ChunkPos.of(chunk), tag);
        chunkbert$ChunkReplacement = tag;
    }

    @Inject(method = "unloadChunk", at = @At("RETURN"))
    private void bobbyReplaceChunk(int chunkX, int chunkZ, CallbackInfo ci, @Local Chunk chunk) {
        NBTTagCompound tag = chunkbert$ChunkReplacement;
        chunkbert$ChunkReplacement = null;

        if (chunkbert$ChunkManager == null || tag == null) {
            // Only notify the tracker if we don't have a fake chunk to replace the unloaded chunk with
            if (!chunk.isEmpty()) {
                ChunkTrackerHolder.get(this.worldObj).onChunkStatusRemoved(chunkX, chunkZ, ChunkStatus.FLAG_ALL);
            }

            return;
        }

        chunkbert$ChunkManager.load(chunkX, chunkZ, tag, chunkbert$ChunkManager.getStorage());
    }

    @Inject(method = "makeString", at = @At("RETURN"), cancellable = true)
    private void bobbyDebugString(CallbackInfoReturnable<String> cir) {
        if (chunkbert$ChunkManager == null) {
            return;
        }

        cir.setReturnValue(cir.getReturnValue() + " " + chunkbert$ChunkManager.getDebugString());
    }
}
