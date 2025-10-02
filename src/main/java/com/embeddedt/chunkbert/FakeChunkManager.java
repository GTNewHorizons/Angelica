package com.embeddedt.chunkbert;

import com.embeddedt.chunkbert.ChunkbertConfig;
import io.netty.util.concurrent.DefaultThreadFactory;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.ThreadedFileIOBase;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class FakeChunkManager {
    private static final String FALLBACK_LEVEL_NAME = "bobby-fallback";
    private static final Minecraft client = Minecraft.getMinecraft();

    private final WorldClient world;
    private final ChunkProviderClient clientChunkManager;
    private int ticksSinceLastSave;
    private final FakeChunkStorage storage;
    private final FakeChunkStorage fallbackStorage;

    private final Long2ObjectMap<Chunk> fakeChunks = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());
    private int centerX, centerZ, viewDistance;
    private final Long2LongMap toBeUnloaded = new Long2LongOpenHashMap();
    // Contains chunks in order to be unloaded. We keep the chunk and time so we can cross-reference it with
    // [toBeUnloaded] to see if the entry has since been removed / the time reset. This way we do not need
    // to remove entries from the middle of the queue.
    private final Deque<Pair<Long, Long>> unloadQueue = new ArrayDeque<>();

    // There unfortunately is only a synchronous api for loading chunks (even though that one just waits on a
    // CompletableFuture, annoying but oh well), so we call that blocking api from a separate thread pool.
    // The size of the pool must be sufficiently large such that there is always at least one query operation
    // running, as otherwise the storage io worker will start writing chunks which slows everything down to a crawl.
    private static final ExecutorService loadExecutor = Executors.newFixedThreadPool(8, new DefaultThreadFactory("bobby-loading", true));
    private final Long2ObjectMap<LoadingJob> loadingJobs = new Long2ObjectOpenHashMap<>();

    public static final int UNLOAD_DELAY_SECS = 60;

    public FakeChunkManager(WorldClient world, ChunkProviderClient clientChunkManager) {
        this.world = world;
        this.clientChunkManager = clientChunkManager;

        long seedHash = 123456; //((BiomeAccessAccessor) world.getBiomeAccess()).getSeed();
        DimensionType worldKey = world.provider.getDimensionType();
        Path storagePath = client.gameDir
                .toPath()
                .resolve(".bobby")
                .resolve(getCurrentWorldOrServerName())
                .resolve(seedHash + "")
                .resolve(worldKey.getName());

        storage = FakeChunkStorage.getFor(storagePath.toFile(), null);

        FakeChunkStorage fallbackStorage = null;
        ISaveFormat levelStorage = client.getSaveLoader();
        if (levelStorage.canLoadWorld(FALLBACK_LEVEL_NAME)) {
            ISaveHandler handler = levelStorage.getSaveLoader(FALLBACK_LEVEL_NAME, false);
            File worldDirectory = handler.getWorldDirectory();
            if(world.provider.getSaveFolder() != null)
                worldDirectory = new File(worldDirectory, world.provider.getSaveFolder());
            File regionDirectory = new File(worldDirectory, "region");
            fallbackStorage = FakeChunkStorage.getFor(regionDirectory, null);
        }
        this.fallbackStorage = fallbackStorage;
    }

    public Chunk getChunk(int x, int z) {
        return fakeChunks.get(ChunkPos.asLong(x, z));
    }

    public FakeChunkStorage getStorage() {
        return storage;
    }

    public void update(BooleanSupplier shouldKeepTicking) {
        // Once a minute, force chunks to disk
        if (++ticksSinceLastSave > 20 * 60) {
            // completeAll is blocking, so we run it on the io pool
            ThreadedFileIOBase.getThreadedIOInstance().queueIO(storage::writeNextIO);

            ticksSinceLastSave = 0;
        }

        EntityPlayerSP player = client.player;
        if (player == null) {
            return;
        }

        long time = System.nanoTime() / 1000000L;

        int oldCenterX = this.centerX;
        int oldCenterZ = this.centerZ;
        int oldViewDistance = this.viewDistance;
        int newCenterX = player.chunkCoordX;
        int newCenterZ = player.chunkCoordZ;
        int newViewDistance = client.gameSettings.renderDistanceChunks;
        if (oldCenterX != newCenterX || oldCenterZ != newCenterZ || oldViewDistance != newViewDistance) {
            // Firstly check which chunks can be unloaded / cancelled
            for (int x = oldCenterX - oldViewDistance; x <= oldCenterX + oldViewDistance; x++) {
                boolean xOutsideNew = x < newCenterX - newViewDistance || x > newCenterX + newViewDistance;
                for (int z = oldCenterZ - oldViewDistance; z <= oldCenterZ + oldViewDistance; z++) {
                    boolean zOutsideNew = z < newCenterZ - newViewDistance || z > newCenterZ + newViewDistance;
                    if (xOutsideNew || zOutsideNew) {
                        cancelLoad(x, z);
                        long chunkPos = ChunkPos.asLong(x, z);
                        toBeUnloaded.put(chunkPos, time);
                        unloadQueue.add(new ImmutablePair<>(chunkPos, time));
                    }
                }
            }

            // Then check which one we need to load
            for (int x = newCenterX - newViewDistance; x <= newCenterX + newViewDistance; x++) {
                boolean xOutsideOld = x < oldCenterX - oldViewDistance || x > oldCenterX + oldViewDistance;
                for (int z = newCenterZ - newViewDistance; z <= newCenterZ + newViewDistance; z++) {
                    boolean zOutsideOld = z < oldCenterZ - oldViewDistance || z > oldCenterZ + oldViewDistance;
                    if (xOutsideOld || zOutsideOld) {
                        long chunkPos = ChunkPos.asLong(x, z);

                        // We want this chunk, so don't unload it if it's still here
                        toBeUnloaded.remove(chunkPos);
                        // Not removing it from [unloadQueue], we check [toBeUnloaded] when we poll it.

                        // If there already is a chunk loaded, there's nothing to do
                        if (clientChunkManager.getLoadedChunk(x, z) != null) {
                            continue;
                        }

                        // All good, load it
                        LoadingJob loadingJob = new LoadingJob(x, z);
                        loadingJobs.put(chunkPos, loadingJob);
                        loadExecutor.execute(loadingJob);
                    }
                }
            }

            this.centerX = newCenterX;
            this.centerZ = newCenterZ;
            this.viewDistance = newViewDistance;
        }

        // Anything remaining in the set is no longer needed and can now be unloaded
        long unloadTime = time - ChunkbertConfig.unloadDelaySecs * 1000L;
        int countSinceLastThrottleCheck = 0;
        while (true) {
            Pair<Long, Long> next = unloadQueue.pollFirst();
            if (next == null) {
                break;
            }
            long chunkPos = next.getLeft();
            long queuedTime = next.getRight();

            if (queuedTime > unloadTime) {
                // Unload is still being delayed, put the entry back into the queue
                // and be done for this update.
                unloadQueue.addFirst(next);
                break;
            }

            long actualQueuedTime = toBeUnloaded.remove(chunkPos);
            if (actualQueuedTime != queuedTime) {
                // The chunk has either been un-queued or re-queued.
                if (actualQueuedTime != 0) {
                    // If it was re-queued, put it back in the map.
                    toBeUnloaded.put(chunkPos, actualQueuedTime);
                }
                // Either way, skip it for now and go to the next entry.
                continue;
            }

            // This chunk is due for unloading
            unload(ChunkPosHelper.getPackedX(chunkPos), ChunkPosHelper.getPackedZ(chunkPos), false);

            if (countSinceLastThrottleCheck++ > 10) {
                countSinceLastThrottleCheck = 0;
                if (!shouldKeepTicking.getAsBoolean()) {
                    break;
                }
            }
        }

        ObjectIterator<LoadingJob> loadingJobsIter = this.loadingJobs.values().iterator();
        while (loadingJobsIter.hasNext()) {
            LoadingJob loadingJob = loadingJobsIter.next();

            //noinspection OptionalAssignedToNull
            if (loadingJob.result == null) {
                continue; // still loading
            }

            // Done loading
            loadingJobsIter.remove();

            client.profiler.startSection("loadFakeChunk");
            loadingJob.complete();
            client.profiler.endSection();

            if (!shouldKeepTicking.getAsBoolean()) {
                break;
            }
        }
    }

    private @Nullable
    Pair<NBTTagCompound, FakeChunkStorage> loadTag(int x, int z) {
        ChunkPos chunkPos = new ChunkPos(x, z);
        NBTTagCompound tag;
        try {
            tag = storage.loadTag(chunkPos);
            if (tag != null) {
                return new ImmutablePair<>(tag, storage);
            }
            if (fallbackStorage != null) {
                tag = fallbackStorage.loadTag(chunkPos);
                if (tag != null) {
                    return new ImmutablePair<>(tag, fallbackStorage);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void load(int x, int z, NBTTagCompound tag, FakeChunkStorage storage) {
        Supplier<Chunk> chunkSupplier = storage.deserialize(new ChunkPos(x, z), tag, world);
        if (chunkSupplier == null) {
            return;
        }
        load(x, z, chunkSupplier.get());
    }

    protected void load(int x, int z, Chunk chunk) {
        fakeChunks.put(ChunkPos.asLong(x, z), chunk);

        world.markBlockRangeForRenderUpdate(x * 16, 0, z * 16, x * 16 + 15, 256, z * 16 + 15);
    }

    public boolean unload(int x, int z, boolean willBeReplaced) {
        cancelLoad(x, z);
        Chunk chunk = fakeChunks.remove(ChunkPos.asLong(x, z));
        if (chunk != null) {
            /* TODO fix lighting */

            world.loadedTileEntityList.removeAll(chunk.getTileEntityMap().values());
            world.tickableTileEntities.removeAll(chunk.getTileEntityMap().values());

            return true;
        }
        return false;
    }

    private void cancelLoad(int x, int z) {
        LoadingJob loadingJob = loadingJobs.remove(ChunkPos.asLong(x, z));
        if (loadingJob != null) {
            loadingJob.cancelled = true;
        }
    }

    private static String getCurrentWorldOrServerName() {
        IntegratedServer integratedServer = client.getIntegratedServer();
        if (integratedServer != null) {
            return integratedServer.getWorldName();
        }

        ServerData serverInfo = client.getCurrentServerData();
        if (serverInfo != null) {
            return serverInfo.serverIP.replace(':', '_');
        }

        if (client.isConnectedToRealms()) {
            return "realms";
        }

        return "unknown";
    }

    public String getDebugString() {
        return "F: " + fakeChunks.size() + " L: " + loadingJobs.size() + " U: " + toBeUnloaded.size();
    }

    private class LoadingJob implements Runnable {
        private final int x;
        private final int z;
        private volatile boolean cancelled;
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType") // null while loading, empty() if no chunk was found
        private volatile Optional<Supplier<Chunk>> result;

        public LoadingJob(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public void run() {
            if (cancelled) {
                return;
            }
            result = Optional.ofNullable(loadTag(x, z))
                    .map(it -> it.getRight().deserialize(new ChunkPos(x, z), it.getLeft(), world));
        }

        public void complete() {
            result.ifPresent(it -> load(x, z, it.get()));
        }
    }
}
