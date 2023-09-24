package org.embeddedt.archaicfix.threadedupdates;

import com.google.common.base.Preconditions;
import lombok.SneakyThrows;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.shader.TesselatorVertexState;
import net.minecraft.world.ChunkCache;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.embeddedt.archaicfix.occlusion.interfaces.IRenderGlobalListener;
import org.embeddedt.archaicfix.occlusion.interfaces.IRendererUpdateOrderProvider;
import org.embeddedt.archaicfix.occlusion.OcclusionHelpers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static org.embeddedt.archaicfix.ArchaicLogger.LOGGER;

public class ThreadedChunkUpdateHelper implements IRenderGlobalListener {

    public static ThreadedChunkUpdateHelper instance;

    public static Thread MAIN_THREAD;

    private static final boolean DEBUG_THREADED_UPDATE_FINE_LOG = Boolean.parseBoolean(System.getProperty("archaicfix.debug.enableThreadedUpdateFineLog"));

    /** Used within the scope of WorldRenderer#updateRenderer (on the main thread). */
    public static WorldRenderer lastWorldRenderer;

    public static final RenderBlocksStack renderBlocksStack = new RenderBlocksStack();

    /** Tasks not yet started */
    public BlockingQueue<WorldRenderer> taskQueue = new LinkedBlockingDeque<>();
    /** Finished tasks ready for consumption */
    public BlockingDeque<WorldRenderer> finishedTasks = new LinkedBlockingDeque<>();

    /** Tasks that should be completed immediately on the main thread */
    public Queue<WorldRenderer> urgentTaskQueue = new ArrayDeque<>();

    public ThreadLocal<Tessellator> threadTessellator = ThreadLocal.withInitial(Tessellator::new);

    IRendererUpdateOrderProvider rendererUpdateOrderProvider = new IRendererUpdateOrderProvider() {
        /** The renderers updated during the batch */
        private List<WorldRenderer> updatedRenderers = new ArrayList<>();

        private WorldRenderer nextRenderer;

        @Override
        public void prepare(List<WorldRenderer> worldRenderersToUpdateList) {
            preRendererUpdates(worldRenderersToUpdateList);
        }

        @Override
        public boolean hasNext(List<WorldRenderer> worldRenderersToUpdateList) {
            WorldRenderer wr;

            if(!urgentTaskQueue.isEmpty()) {
                nextRenderer = urgentTaskQueue.poll();
                UpdateTask task = ((IRendererUpdateResultHolder)nextRenderer).arch$getRendererUpdateTask();
                task.cancelled = true;
                return true;
            }

            while((wr = finishedTasks.poll()) != null) {
                UpdateTask task = ((IRendererUpdateResultHolder)wr).arch$getRendererUpdateTask();
                if(task.cancelled || !wr.needsUpdate) {
                    task.clear();
                } else {
                    nextRenderer = wr;
                    return true;
                }
            }
            return false;
        }

        @Override
        public WorldRenderer next(List<WorldRenderer> worldRenderersToUpdateList) {
            Preconditions.checkNotNull(nextRenderer);
            WorldRenderer wr = nextRenderer;
            nextRenderer = null;
            updatedRenderers.add(wr);

            debugLog("Consuming renderer " + worldRendererToString(wr) + " " + worldRendererUpdateTaskToString(wr));

            return wr;
        }

        @Override
        public void cleanup(List<WorldRenderer> worldRenderersToUpdateList) {
            for(WorldRenderer wr : updatedRenderers) {
                worldRenderersToUpdateList.remove(wr);
                ((IRendererUpdateResultHolder)wr).arch$getRendererUpdateTask().clear();
            }
            updatedRenderers.clear();
            urgentTaskQueue.clear();
            nextRenderer = null;
        }
    };

    public void init() {
        OcclusionHelpers.renderer.arch$setRendererUpdateOrderProvider(rendererUpdateOrderProvider);
        OcclusionHelpers.renderer.arch$addRenderGlobalListener(this);
        MAIN_THREAD = Thread.currentThread();

        LOGGER.info("Creating " + ArchaicConfig.numChunkUpdateThreads + " chunk builder" + (ArchaicConfig.numChunkUpdateThreads > 1 ? "s" : ""));
        for(int i = 0; i < ArchaicConfig.numChunkUpdateThreads; i++) {
            new Thread(this::runThread, "Chunk Update Worker Thread #" + i).start();
        }
    }

    private void preRendererUpdates(List<WorldRenderer> toUpdateList) {
        updateWorkQueue(toUpdateList);
        removeCancelledResults();
    }

    private void updateWorkQueue(List<WorldRenderer> toUpdateList) {
        final int updateQueueSize = 64; // TODO decide this dynamically
        taskQueue.clear();
        for(int i = 0; i < updateQueueSize && i < toUpdateList.size(); i++) {
            WorldRenderer wr = toUpdateList.get(i);
            UpdateTask task = ((IRendererUpdateResultHolder)wr).arch$getRendererUpdateTask();

            if(wr.distanceToEntitySquared(Minecraft.getMinecraft().renderViewEntity) < 16 * 16) {
                if(!ArchaicConfig.disableBlockingChunkUpdates){
                    urgentTaskQueue.add(wr);
                } else {
                    task.important = true;
                }
            }

            if(task.isEmpty()) {
                // No update in progress; add to task queue
                debugLog("Adding " + worldRendererToString(wr) + " to task queue");
                task.chunkCache = getChunkCacheSnapshot(wr);
                taskQueue.add(wr);
            }
        }
    }

    private void removeCancelledResults() {
        for(Iterator<WorldRenderer> it = finishedTasks.iterator(); it.hasNext(); ) {
            WorldRenderer wr = it.next();
            UpdateTask task = ((IRendererUpdateResultHolder)wr).arch$getRendererUpdateTask();
            if(task.cancelled) {
                // Discard results and allow re-schedule on worker thread.
                task.clear();
                it.remove();
            }
        }
    }

    @Override
    public void onDirtyRendererChanged(WorldRenderer wr) {
        onWorldRendererDirty(wr);
    }

    public void onWorldRendererDirty(WorldRenderer wr) {
        UpdateTask task = ((IRendererUpdateResultHolder)wr).arch$getRendererUpdateTask();
        if(!task.isEmpty()) {
            debugLog("Renderer " + worldRendererToString(wr) + " is dirty, cancelling task");
            task.cancelled = true;
        }
    }

    @SneakyThrows
    private void runThread() {
        while(true) {
            WorldRenderer wr = taskQueue.take();
            UpdateTask task = ((IRendererUpdateResultHolder)wr).arch$getRendererUpdateTask();
            task.started = true;
            try {
                doChunkUpdate(wr);
            } catch(Exception e) {
                LOGGER.error("Failed to update chunk " + worldRendererToString(wr));
                e.printStackTrace();
                for(UpdateTask.Result r : task.result) {
                    r.clear();
                }
                ((ICapturableTessellator)threadTessellator.get()).discard();
            }
            if(!task.important) {
                finishedTasks.add(wr);
            } else {
                finishedTasks.addFirst(wr);
            }

        }
    }

    /** Renders certain blocks (as defined in canBlockBeRenderedOffThread) on the worker thread, and saves the
     *  tessellation result. WorldRenderer#updateRenderer will skip over these blocks, and use the result that was
     *  produced by the worker thread to fill them in.
     */
    public void doChunkUpdate(WorldRenderer wr) {
        debugLog("Starting update of renderer " + worldRendererToString(wr));

        UpdateTask task = ((IRendererUpdateResultHolder)wr).arch$getRendererUpdateTask();

        ChunkCache chunkcache = task.chunkCache;

        Tessellator tess = threadTessellator.get();

        if(chunkcache != null && !chunkcache.extendedLevelsInChunkCache()) {
            RenderBlocks renderblocks = new RenderBlocks(chunkcache);

            for(int pass = 0; pass < 2; pass++) {
                boolean renderedSomething = false;
                boolean startedTessellator = false;

                BlockLoop:
                for (int y = wr.posY; y < wr.posY + 16; ++y) {
                    for (int z = wr.posZ; z < wr.posZ + 16; ++z) {
                        for (int x = wr.posX; x < wr.posX + 16; ++x) {
                            if(task.cancelled) {
                                debugLog("Realized renderer " + worldRendererToString(wr) + " is dirty, aborting update");
                                break BlockLoop;
                            }

                            Block block = chunkcache.getBlock(x, y, z);

                            if (block.getMaterial() != Material.air) {
                                if (!startedTessellator) {
                                    startedTessellator = true;
                                    tess.startDrawingQuads();
                                    tess.setTranslation(-wr.posX, -wr.posY, -wr.posZ);
                                }

                                int k3 = block.getRenderBlockPass();

                                if (!block.canRenderInPass(pass)) continue;

                                renderedSomething |= renderblocks.renderBlockByRenderType(block, x, y, z);
                            }
                        }
                    }
                }

                if (startedTessellator) {
                    task.result[pass].renderedQuads = ((ICapturableTessellator) tess).arch$getUnsortedVertexState();
                    ((ICapturableTessellator) tess).discard();
                }
                task.result[pass].renderedSomething = renderedSomething;
            }
        }
        debugLog("Result of updating " + worldRendererToString(wr) + ": " + worldRendererUpdateTaskToString(wr));
    }

    public static boolean canBlockBeRenderedOffThread(Block block, int pass, int renderType) {
        return renderType < 42 && renderType != 22; // vanilla block
    }

    private ChunkCache getChunkCacheSnapshot(WorldRenderer wr) {
        // TODO This is not thread-safe! Actually make a snapshot here.
        byte pad = 1;
        ChunkCache chunkcache = new ChunkCache(wr.worldObj, wr.posX - pad, wr.posY - pad, wr.posZ - pad,
                wr.posX + 16 + pad, wr.posY + 16 + pad, wr.posZ + 16 + pad, pad);
        return chunkcache;
    }

    public void clear() {
        // TODO: destroy state when chunks are reloaded or server is stopped
    }

    public Tessellator getThreadTessellator() {
        if(Thread.currentThread() == MAIN_THREAD) {
            return Tessellator.instance;
        } else {
            return threadTessellator.get();
        }
    }

    private static String worldRendererToString(WorldRenderer wr) {
        return wr + "(" + wr.posX + ", " + wr.posY + ", " + wr.posZ + ")";
    }

    private static String worldRendererUpdateTaskToString(WorldRenderer wr) {
        UpdateTask task = ((IRendererUpdateResultHolder)wr).arch$getRendererUpdateTask();
        return task.result[0].renderedSomething + " (" + (task.result[0].renderedQuads == null ? "null" : task.result[0].renderedQuads.getVertexCount()) + ")/" + task.result[1].renderedSomething + " (" + (task.result[1].renderedQuads == null ? "null" : task.result[1].renderedQuads.getVertexCount()) + ")";
    }

    private static void debugLog(String msg) {
        if(DEBUG_THREADED_UPDATE_FINE_LOG) {
            LOGGER.trace(msg);
        }
    }

    public static class UpdateTask {
        public boolean started;
        public boolean cancelled;
        public boolean important;
        public Result[] result = new Result[]{new Result(), new Result()};

        public ChunkCache chunkCache;

        public boolean isEmpty() {
            return !started;
        }

        public void clear() {
            started = false;
            chunkCache = null;
            for(Result r : result) {
                r.clear();
            }
            cancelled = false;
            important = false;
        }

        public static class Result {
            public boolean renderedSomething;
            public TesselatorVertexState renderedQuads;

            public void clear() {
                renderedSomething = false;
                renderedQuads = null;
            }
        }
    }
}
