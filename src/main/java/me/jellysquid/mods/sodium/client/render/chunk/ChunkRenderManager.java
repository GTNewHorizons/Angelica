package me.jellysquid.mods.sodium.client.render.chunk;

import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.compat.mojang.ChunkPos;
import com.gtnewhorizons.angelica.compat.mojang.MatrixStack;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.Getter;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.compat.FogHelper;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw.MultidrawChunkRenderBackend;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.cull.ChunkCuller;
import me.jellysquid.mods.sodium.client.render.chunk.cull.ChunkFaceFlags;
import me.jellysquid.mods.sodium.client.render.chunk.cull.graph.ChunkGraphCuller;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterator;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPassManager;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import me.jellysquid.mods.sodium.common.util.IdTable;
import me.jellysquid.mods.sodium.common.util.collections.FutureDequeDrain;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;

public class ChunkRenderManager<T extends ChunkGraphicsState> implements ChunkStatusListener {
    /**
     * The maximum distance a chunk can be from the player's camera in order to be eligible for blocking updates.
     */
    private static final double NEARBY_CHUNK_DISTANCE = Math.pow(48, 2.0);

    /**
     * The minimum distance the culling plane can be from the player's camera. This helps to prevent mathematical
     * errors that occur when the fog distance is less than 8 blocks in width, such as when using a blindness potion.
     */
    private static final float FOG_PLANE_MIN_DISTANCE = (float) Math.pow(8.0f, 2.0);

    /**
     * The distance past the fog's far plane at which to begin culling. Distance calculations use the center of each
     * chunk from the camera's position, and as such, special care is needed to ensure that the culling plane is pushed
     * back far enough. I'm sure there's a mathematical formula that should be used here in place of the constant,
     * but this value works fine in testing.
     */
    private static final float FOG_PLANE_OFFSET = 12.0f;

    private final ChunkBuilder<T> builder;
    private final ChunkRenderBackend<T> backend;

    private final Thread renderThread = Thread.currentThread();

    private final Long2ObjectOpenHashMap<ChunkRenderColumn<T>> columns = new Long2ObjectOpenHashMap<>();
    private final IdTable<ChunkRenderContainer<T>> renders = new IdTable<>(16384);

    private final ObjectArrayFIFOQueue<ChunkRenderContainer<T>> importantRebuildQueue = new ObjectArrayFIFOQueue<>();
    private final ObjectArrayFIFOQueue<ChunkRenderContainer<T>> rebuildQueue = new ObjectArrayFIFOQueue<>();
    private final ObjectArrayFIFOQueue<ChunkRenderContainer<T>> sortQueue = new ObjectArrayFIFOQueue<>();
    private final ObjectArrayFIFOQueue<ChunkRenderContainer<T>> unloadQueue = new ObjectArrayFIFOQueue<>();

    @SuppressWarnings("unchecked")
    private final ChunkRenderList<T>[] chunkRenderLists = new ChunkRenderList[BlockRenderPass.COUNT];
    private final ObjectList<ChunkRenderContainer<T>> tickableChunks = new ObjectArrayList<>();

    private final ObjectList<TileEntity> visibleBlockEntities = new ObjectArrayList<>();

    private final SodiumWorldRenderer renderer;
    private final WorldClient world;

    private final ChunkCuller culler;
    private final boolean useBlockFaceCulling;

    private float cameraX, cameraY, cameraZ;
    private boolean dirty;

    @Getter
    private int submitted;
    private int totalSubmitted;

    private final boolean translucencySorting;

    @Getter
    private int visibleChunkCount;

    private boolean useFogCulling;
    private double fogRenderCutoff;

    private final int translucencyBlockRenderDistance;

    private boolean alwaysDeferChunkUpdates;

    public ChunkRenderManager(SodiumWorldRenderer renderer, ChunkRenderBackend<T> backend, BlockRenderPassManager renderPassManager, WorldClient world, int renderDistance) {
        this.backend = backend;
        this.renderer = renderer;
        this.world = world;

        this.builder = new ChunkBuilder<>(backend.getVertexType(), this.backend);
        this.builder.init(world, renderPassManager);

        this.dirty = true;

        for (int i = 0; i < this.chunkRenderLists.length; i++) {
            this.chunkRenderLists[i] = new ChunkRenderList<>();
        }

        this.culler = new ChunkGraphCuller(world, renderDistance);
        this.translucencySorting = SodiumClientMod.options().advanced.translucencySorting;
        this.translucencyBlockRenderDistance = Math.min(9216, (renderDistance << 4) * (renderDistance << 4));

        // TODO: Sodium - Culling
        this.useBlockFaceCulling = false; //SodiumClientMod.options().advanced.useBlockFaceCulling;
    }

    public void update(Camera camera, FrustumExtended frustum, int frame, boolean spectator) {
        this.reset();
        this.unloadPending();

        this.setup(camera);
        this.iterateChunks(camera, frustum, frame, spectator);

        this.dirty = false;
    }

    public int getRebuildQueueSize() {
        return this.rebuildQueue.size();
    }

    public int getImportantRebuildQueueSize() {
        return this.importantRebuildQueue.size();
    }

    private void setup(Camera camera) {
        Vector3d cameraPos = camera.getPos();

        this.cameraX = (float) cameraPos.x;
        this.cameraY = (float) cameraPos.y;
        this.cameraZ = (float) cameraPos.z;

        this.useFogCulling = false;
        this.alwaysDeferChunkUpdates = SodiumClientMod.options().performance.alwaysDeferChunkUpdates;

        if (SodiumClientMod.options().advanced.useFogOcclusion) {
            float dist = FogHelper.getFogCutoff() + FOG_PLANE_OFFSET;

            if (dist != 0.0f) {
                // TODO: Sodium - Culling
                //this.useFogCulling = true; // TODO fix fog
                this.fogRenderCutoff = Math.max(FOG_PLANE_MIN_DISTANCE, dist * dist);
            }
        }
    }

    private void iterateChunks(Camera camera, FrustumExtended frustum, int frame, boolean spectator) {
        // Schedule new translucency sorting tasks if the camera has moved
        if(this.translucencySorting) {
            this.checkTranslucencyCameraMoved();
            if(this.hasCameraMovedTranslucent) {
                for(Object o : this.renders.getElements()) {
                    if(o == null)
                        continue;
                    ChunkRenderContainer<T> render = (ChunkRenderContainer<T>)o;
                    if(render.getData().isEmpty())
                        continue;
                    if(!render.needsRebuild() && render.canRebuild() && render.shouldRebuildForTranslucents() && render.getSquaredDistance(cameraX, cameraY, cameraZ) < translucencyBlockRenderDistance) {
                        // put it at the end of the queue, after any "real" rebuild tasks
                        render.scheduleSort(false);
                    }
                }
            }
        }

        IntList list = this.culler.computeVisible(camera, frustum, frame, spectator);
        IntIterator it = list.iterator();

        while (it.hasNext()) {
            ChunkRenderContainer<T> render = this.renders.get(it.nextInt());

            this.addChunk(render);
        }
    }

    private float lastCameraTranslucentX, lastCameraTranslucentY, lastCameraTranslucentZ;
    private boolean hasCameraMovedTranslucent;

    private void checkTranslucencyCameraMoved() {
        float dx = (cameraX - lastCameraTranslucentX);
        float dy = (cameraY - lastCameraTranslucentY);
        float dz = (cameraZ - lastCameraTranslucentZ);
        if((dx * dx + dy * dy + dz * dz) > 1.0) {
            lastCameraTranslucentX = cameraX;
            lastCameraTranslucentY = cameraY;
            lastCameraTranslucentZ = cameraZ;
            hasCameraMovedTranslucent = true;
        } else
            hasCameraMovedTranslucent = false;
    }

    private void addChunk(ChunkRenderContainer<T> render) {
        if (render.needsRebuild() && render.canRebuild()) {
            if (!this.alwaysDeferChunkUpdates && render.needsImportantRebuild()) {
                this.importantRebuildQueue.enqueue(render);
            } else {
                this.rebuildQueue.enqueue(render);
            }
        } else if (render.canRebuild() && !render.getData().isEmpty() && render.needsSort()) {
            this.sortQueue.enqueue(render);
        }

        if (this.useFogCulling && render.getSquaredDistanceXZ(this.cameraX, this.cameraZ) >= this.fogRenderCutoff) {
            return;
        }

        if (!render.isEmpty()) {
            this.addChunkToRenderLists(render);
            this.addEntitiesToRenderLists(render);
        }
    }

    private void addChunkToRenderLists(ChunkRenderContainer<T> render) {
        int visibleFaces = this.computeVisibleFaces(render) & render.getFacesWithData();

        if (visibleFaces == 0) {
            return;
        }

        boolean added = false;
        T[] states = render.getGraphicsStates();

        for (int i = 0; i < states.length; i++) {
            T state = states[i];

            if (state != null) {
                ChunkRenderList<T> list = this.chunkRenderLists[i];
                list.add(state, (this.translucencySorting && BlockRenderPass.VALUES[i].isTranslucent()) ? (ChunkFaceFlags.ALL & render.getFacesWithData()) : visibleFaces);

                added = true;
            }
        }

        if (added) {
            if (render.isTickable()) {
                this.tickableChunks.add(render);
            }

            this.visibleChunkCount++;
        }
    }

    private int computeVisibleFaces(ChunkRenderContainer<T> render) {
        // If chunk face culling is disabled, render all faces
        if (!this.useBlockFaceCulling) {
            return ChunkFaceFlags.ALL;
        }

        ChunkRenderBounds bounds = render.getBounds();

        // Always render groups of vertices not belonging to any given face
        int visibleFaces = ChunkFaceFlags.UNASSIGNED;

        if (this.cameraY > bounds.y1) {
            visibleFaces |= ChunkFaceFlags.UP;
        }

        if (this.cameraY < bounds.y2) {
            visibleFaces |= ChunkFaceFlags.DOWN;
        }

        if (this.cameraX > bounds.x1) {
            visibleFaces |= ChunkFaceFlags.EAST;
        }

        if (this.cameraX < bounds.x2) {
            visibleFaces |= ChunkFaceFlags.WEST;
        }

        if (this.cameraZ > bounds.z1) {
            visibleFaces |= ChunkFaceFlags.SOUTH;
        }

        if (this.cameraZ < bounds.z2) {
            visibleFaces |= ChunkFaceFlags.NORTH;
        }

        return visibleFaces;
    }

    private void addEntitiesToRenderLists(ChunkRenderContainer<T> render) {
        Collection<TileEntity> tileEntities = render.getData().getTileEntities();

        if (!tileEntities.isEmpty()) {
            this.visibleBlockEntities.addAll(tileEntities);
        }
    }

    public ChunkRenderContainer<T> getRender(int x, int y, int z) {
        ChunkRenderColumn<T> column = this.columns.get(ChunkPos.toLong(x, z));

        if (column == null) {
            return null;
        }

        return column.getRender(y);
    }

    private void reset() {
        this.rebuildQueue.clear();
        this.importantRebuildQueue.clear();
        this.sortQueue.clear();

        this.visibleBlockEntities.clear();

        for (ChunkRenderList<T> list : this.chunkRenderLists) {
            list.reset();
        }

        this.tickableChunks.clear();

        this.visibleChunkCount = 0;
    }

    private void unloadPending() {
        while (!this.unloadQueue.isEmpty()) {
            this.unloadQueue.dequeue()
                    .delete();
        }
    }

    public Collection<TileEntity> getVisibleBlockEntities() {
        return this.visibleBlockEntities;
    }

    @Override
    public void onChunkAdded(int x, int z) {
        this.loadChunk(x, z);
    }

    @Override
    public void onChunkRemoved(int x, int z) {
        this.unloadChunk(x, z);
    }

    private void loadChunk(int x, int z) {
        ChunkRenderColumn<T> column = new ChunkRenderColumn<>(x, z);
        ChunkRenderColumn<T> prev;

        if ((prev = this.columns.put(ChunkPos.toLong(x, z), column)) != null) {
            this.unloadSections(prev);
        }

        this.connectNeighborColumns(column);
        this.loadSections(column);

        this.dirty = true;
    }

    private void unloadChunk(int x, int z) {
        ChunkRenderColumn<T> column = this.columns.remove(ChunkPos.toLong(x, z));

        if (column == null) {
            return;
        }

        this.disconnectNeighborColumns(column);
        this.unloadSections(column);

        this.dirty = true;
    }

    private void loadSections(ChunkRenderColumn<T> column) {
        int x = column.getX();
        int z = column.getZ();

        for (int y = 0; y < 16; y++) {
            ChunkRenderContainer<T> render = this.createChunkRender(column, x, y, z);
            column.setRender(y, render);

            this.culler.onSectionLoaded(x, y, z, render.getId());
        }
    }

    private void unloadSections(ChunkRenderColumn<T> column) {
        int x = column.getX();
        int z = column.getZ();

        for (int y = 0; y < 16; y++) {
            ChunkRenderContainer<T> render = column.getRender(y);

            if (render != null) {
                this.unloadQueue.enqueue(render);
                this.renders.remove(render.getId());
            }

            this.culler.onSectionUnloaded(x, y, z);
        }
    }

    private void connectNeighborColumns(ChunkRenderColumn<T> column) {
        for (ForgeDirection dir : DirectionUtil.ALL_DIRECTIONS) {
            ChunkRenderColumn<T> adj = this.getAdjacentColumn(column, dir);

            if (adj != null) {
                adj.setAdjacentColumn(dir.getOpposite(), column);
            }

            column.setAdjacentColumn(dir, adj);
        }
    }

    private void disconnectNeighborColumns(ChunkRenderColumn<T> column) {
        for (ForgeDirection dir : DirectionUtil.ALL_DIRECTIONS) {
            ChunkRenderColumn<T> adj = column.getAdjacentColumn(dir);

            if (adj != null) {
                adj.setAdjacentColumn(dir.getOpposite(), null);
            }

            column.setAdjacentColumn(dir, null);
        }
    }

    private ChunkRenderColumn<T> getAdjacentColumn(ChunkRenderColumn<T> column, ForgeDirection dir) {
        return this.getColumn(column.getX() + dir.offsetX, column.getZ() + dir.offsetZ);
    }

    private ChunkRenderColumn<T> getColumn(int x, int z) {
        return this.columns.get(ChunkPos.toLong(x, z));
    }

    private ChunkRenderContainer<T> createChunkRender(ChunkRenderColumn<T> column, int x, int y, int z) {
        ChunkRenderContainer<T> render = new ChunkRenderContainer<>(this.backend, this.renderer, x, y, z, column);
        final ExtendedBlockStorage array = this.world.getChunkFromChunkCoords(x, z).getBlockStorageArray()[y];

        if (array == null || array.isEmpty()) {
            render.setData(ChunkRenderData.EMPTY);
        } else {
            render.scheduleRebuild(false);
        }

        render.setId(this.renders.add(render));

        return render;
    }

    public void renderLayer(MatrixStack matrixStack, BlockRenderPass pass, double x, double y, double z) {
        ChunkRenderList<T> chunkRenderList = this.chunkRenderLists[pass.ordinal()];
        ChunkRenderListIterator<T> iterator = chunkRenderList.iterator(pass.isTranslucent());

        RenderDevice device = RenderDevice.INSTANCE;
        CommandList commandList = device.createCommandList();

        this.backend.begin(matrixStack);
        // Ensure multidraw regions are ordered appropriately
        if(this.backend instanceof MultidrawChunkRenderBackend) {
            ((MultidrawChunkRenderBackend) this.backend).setReverseRegions(pass.isTranslucent());
        }
        this.backend.render(commandList, iterator, new ChunkCameraContext(x, y, z));
        this.backend.end(matrixStack);

        commandList.flush();
    }

    public void tickVisibleRenders() {
        for (ChunkRenderContainer<T> render : this.tickableChunks) {
            render.tick();
        }
    }

    public boolean isChunkVisible(int x, int y, int z) {
        return this.culler.isSectionVisible(x, y, z);
    }

    public void updateChunks() {
        this.builder.cleanupSectionCache();

        Deque<CompletableFuture<ChunkBuildResult<T>>> futures = new ArrayDeque<>();

        int budget = this.builder.getSchedulingBudget();
        submitted = 0;

        while (!this.importantRebuildQueue.isEmpty()) {
            ChunkRenderContainer<T> render = this.importantRebuildQueue.dequeue();

            if (render == null) {
                continue;
            }

            // Do not allow distant chunks to block rendering
            if (this.alwaysDeferChunkUpdates || !this.isChunkPrioritized(render)) {
                this.builder.deferRebuild(render);
            } else {
                futures.add(this.builder.scheduleRebuildTaskAsync(render));
            }

            this.dirty = true;
            submitted++;
            // Limit quantity of updates submitted if we are deferring all important builds
            if (this.alwaysDeferChunkUpdates && submitted >= budget)
                break;
        }

        while (submitted < budget && !this.rebuildQueue.isEmpty()) {
            ChunkRenderContainer<T> render = this.rebuildQueue.dequeue();

            this.builder.deferRebuild(render);
            submitted++;
        }

        // always do at least one sort
        boolean sortedAnything = false;
        while ((!sortedAnything || submitted < budget) && !this.sortQueue.isEmpty()) {
            ChunkRenderContainer<T> render = this.sortQueue.dequeue();

            this.builder.deferSort(render);
            sortedAnything = true;
            submitted++;
        }
        totalSubmitted += submitted;

        this.dirty |= submitted > 0;

        // Try to complete some other work on the main thread while we wait for rebuilds to complete
        this.dirty |= this.builder.performPendingUploads();

        if (!futures.isEmpty()) {
            this.backend.upload(RenderDevice.INSTANCE.createCommandList(), new FutureDequeDrain<>(futures));
        }
    }

    public int getAndResetSubmitted() {
        // Return how many chunks were submitted since the last call to this method
        final int submitted = totalSubmitted;
        totalSubmitted = 0;
        return submitted;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void restoreChunks(LongCollection chunks) {
        LongIterator it = chunks.iterator();

        while (it.hasNext()) {
            long pos = it.nextLong();

            this.loadChunk(ChunkPos.getPackedX(pos), ChunkPos.getPackedZ(pos));
        }
    }

    public boolean isBuildComplete() {
        return this.builder.isBuildQueueEmpty();
    }

    public void setCameraPosition(double x, double y, double z) {
        this.builder.setCameraPosition(x, y, z);
    }

    public void destroy() {
        this.reset();

        for (ChunkRenderColumn<T> column : this.columns.values()) {
            this.unloadSections(column);
        }

        this.columns.clear();

        this.builder.stopWorkers();
    }

    public int getTotalSections() {
        return this.columns.size() * 16;
    }

    private void scheduleRebuildOffThread(int x, int y, int z, boolean important) {
        // TODO: Sodium Threads
        throw new RuntimeException("scheduleRebuildOffThread");
//        Minecraft.getMinecraft().submit(() -> this.scheduleRebuild(x, y, z, important));
    }

    public void scheduleRebuild(int x, int y, int z, boolean important) {
        // Some mods try to schedule rebuilds off the main thread. This is dangerous,
        // but the vanilla renderer seems to tolerate it. Our hashmaps are not thread-safe
        // so the best solution is to do what the server chunk system does and handle this
        // on the main thread.
        if (Thread.currentThread() != renderThread) {
            this.scheduleRebuildOffThread(x, y, z, important);
            return;
        }

        ChunkRenderContainer<T> render = this.getRender(x, y, z);

        if (render != null) {
            // Nearby chunks are always rendered immediately
            important = important || this.isChunkPrioritized(render);

            // Only enqueue chunks for updates if they aren't already enqueued for an update
            //
            // We should avoid rebuilding chunks that aren't visible by using data from the occlusion culler, however
            // that is not currently feasible because occlusion culling data is only ever updated when chunks are
            // rebuilt. Computation of occlusion data needs to be isolated from chunk rebuilds for that to be feasible.
            //
            // TODO: Avoid rebuilding chunks that aren't visible to the player
            if (render.scheduleRebuild(important)) {
                (render.needsImportantRebuild() ? this.importantRebuildQueue : this.rebuildQueue)
                        .enqueue(render);
            }

            this.dirty = true;
        }

        this.builder.onChunkDataChanged(x, y, z);
    }

    public boolean isChunkPrioritized(ChunkRenderContainer<T> render) {
        return render != null ? render.getSquaredDistance(this.cameraX, this.cameraY, this.cameraZ) <= NEARBY_CHUNK_DISTANCE : false;
    }

    public void onChunkRenderUpdates(int x, int y, int z, ChunkRenderData data) {
        this.culler.onSectionStateChanged(x, y, z, data.getOcclusionData());
    }
}
