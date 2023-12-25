package me.jellysquid.mods.sodium.mixin.features.chunk_rendering;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListenerManager;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.source.BiomeArray;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BooleanSupplier;

@Mixin(ClientChunkManager.class)
public abstract class MixinClientChunkManager implements ChunkStatusListenerManager {
    @Shadow
    @Nullable
    public abstract WorldChunk getChunk(int i, int j, ChunkStatus chunkStatus, boolean bl);

    private final LongOpenHashSet loadedChunks = new LongOpenHashSet();
    private boolean needsTrackingUpdate = false;

    private ChunkStatusListener listener;

    @Inject(method = "loadChunkFromPacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;resetChunkColor(II)V", shift = At.Shift.AFTER))
    private void afterLoadChunkFromPacket(int x, int z, BiomeArray biomes, PacketByteBuf buf, NbtCompound tag, int verticalStripBitmask, boolean complete, CallbackInfoReturnable<WorldChunk> cir) {
        if (this.listener != null) {
            this.listener.onChunkAdded(x, z);
            this.loadedChunks.add(ChunkPos.toLong(x, z));
        }
    }

    @Inject(method = "unload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientChunkManager$ClientChunkMap;compareAndSet(ILnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/chunk/WorldChunk;)Lnet/minecraft/world/chunk/WorldChunk;", shift = At.Shift.AFTER))
    private void afterUnloadChunk(int x, int z, CallbackInfo ci) {
        if (this.listener != null) {
            this.listener.onChunkRemoved(x, z);
            this.loadedChunks.remove(ChunkPos.toLong(x, z));
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void afterTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        if (!this.needsTrackingUpdate) {
            return;
        }

        LongIterator it = this.loadedChunks.iterator();

        while (it.hasNext()) {
            long pos = it.nextLong();

            int x = ChunkPos.getPackedX(pos);
            int z = ChunkPos.getPackedZ(pos);

            if (this.getChunk(x, z, ChunkStatus.FULL, false) == null) {
                it.remove();

                if (this.listener != null) {
                    this.listener.onChunkRemoved(x, z);
                }
            }
        }

        this.needsTrackingUpdate = false;
    }

    @Inject(method = "setChunkMapCenter(II)V", at = @At("RETURN"))
    private void afterChunkMapCenterChanged(int x, int z, CallbackInfo ci) {
        this.needsTrackingUpdate = true;
    }

    @Inject(method = "updateLoadDistance",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/world/ClientChunkManager$ClientChunkMap;set(ILnet/minecraft/world/chunk/WorldChunk;)V",
                    shift = At.Shift.AFTER))
    private void afterLoadDistanceChanged(int loadDistance, CallbackInfo ci) {
        this.needsTrackingUpdate = true;
    }

    @Override
    public void setListener(ChunkStatusListener listener) {
        this.listener = listener;
    }

    @Mixin(targets = "net/minecraft/client/world/ClientChunkManager$ClientChunkMap")
    public static class MixinClientChunkMap {
        @Mutable
        @Shadow
        @Final
        private AtomicReferenceArray<WorldChunk> chunks;

        @Mutable
        @Shadow
        @Final
        private int diameter;

        @Mutable
        @Shadow
        @Final
        private int radius;

        private int factor;

        @Inject(method = "<init>", at = @At("RETURN"))
        private void reinit(ClientChunkManager outer, int loadDistance, CallbackInfo ci) {
            // This re-initialization is a bit expensive on memory, but it only happens when either the world is
            // switched or the render distance is changed;
            this.radius = loadDistance;

            // Make the diameter a power-of-two so we can exploit bit-wise math when computing indices
            this.diameter = MathHelper.smallestEncompassingPowerOfTwo(loadDistance * 2 + 1);

            // The factor is used as a bit mask to replace the modulo in getIndex
            this.factor = this.diameter - 1;

            this.chunks = new AtomicReferenceArray<>(this.diameter * this.diameter);
        }

        /**
         * @reason Avoid expensive modulo
         * @author JellySquid
         */
        @Overwrite
        private int getIndex(int chunkX, int chunkZ) {
            return (chunkZ & this.factor) * this.diameter + (chunkX & this.factor);
        }
    }
}
