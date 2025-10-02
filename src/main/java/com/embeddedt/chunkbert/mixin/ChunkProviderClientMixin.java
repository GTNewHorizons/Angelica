package com.embeddedt.chunkbert.mixin;

import com.embeddedt.chunkbert.ChunkbertConfig;
import com.embeddedt.chunkbert.FakeChunkManager;
import com.embeddedt.chunkbert.FakeChunkStorage;
import com.embeddedt.chunkbert.ext.IChunkProviderClient;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(ChunkProviderClient.class)
public class ChunkProviderClientMixin implements IChunkProviderClient {
    @Shadow @Final private Chunk blankChunk;
    @Shadow @Final private World world;
    @Nullable
    protected FakeChunkManager bobbyChunkManager = null;
    // Cache of chunk which was just unloaded so we can immediately
    // load it again without having to wait for the storage io worker.
    protected @Nullable NBTTagCompound bobbyChunkReplacement;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bobbyInit(World worldIn, CallbackInfo ci) {
        if(ChunkbertConfig.enabled)
            bobbyChunkManager = new FakeChunkManager((WorldClient)worldIn, (ChunkProviderClient) (Object) this);
    }

    @Nullable
    @Override
    public FakeChunkManager getBobbyChunkManager() {
        return bobbyChunkManager;
    }

    @Inject(method = "provideChunk", at = @At("RETURN"), cancellable = true)
    private void bobbyGetChunk(int x, int z, CallbackInfoReturnable<Chunk> ci) {
        // Did we find a live chunk?
        if (ci.getReturnValue() != blankChunk) {
            return;
        }

        if (bobbyChunkManager == null) {
            return;
        }

        // Otherwise, see if we've got one
        Chunk chunk = bobbyChunkManager.getChunk(x, z);
        if (chunk != null) {
            ci.setReturnValue(chunk);
        }
    }

    @Inject(method = "unloadChunk", at = @At("HEAD"))
    private void bobbySaveChunk(int chunkX, int chunkZ, CallbackInfo ci) {
        if (bobbyChunkManager == null) {
            return;
        }

        Chunk chunk = world.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return;
        }

        FakeChunkStorage storage = bobbyChunkManager.getStorage();
        NBTTagCompound tag = storage.serialize(chunk);
        storage.save(chunk.getPos(), tag);
        bobbyChunkReplacement = tag;
    }

    @Inject(method = "unloadChunk", at = @At("RETURN"))
    private void bobbyReplaceChunk(int chunkX, int chunkZ, CallbackInfo ci) {
        if (bobbyChunkManager == null) {
            return;
        }

        NBTTagCompound tag = bobbyChunkReplacement;
        bobbyChunkReplacement = null;
        if (tag == null) {
            return;
        }
        bobbyChunkManager.load(chunkX, chunkZ, tag, bobbyChunkManager.getStorage());
    }

    @Inject(method = "makeString", at = @At("RETURN"), cancellable = true)
    private void bobbyDebugString(CallbackInfoReturnable<String> cir) {
        if (bobbyChunkManager == null) {
            return;
        }

        cir.setReturnValue(cir.getReturnValue() + " " + bobbyChunkManager.getDebugString());
    }
}
