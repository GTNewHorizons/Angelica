package org.embeddedt.archaicfix.occlusion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderList;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.util.RenderDistanceSorter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import org.embeddedt.archaicfix.helpers.WorldRendererDistanceHelper;
import org.embeddedt.archaicfix.occlusion.interfaces.ICulledChunk;
import org.embeddedt.archaicfix.occlusion.interfaces.IRenderGlobalListener;
import org.embeddedt.archaicfix.occlusion.interfaces.IRendererUpdateOrderProvider;
import org.embeddedt.archaicfix.occlusion.interfaces.IWorldRenderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class OcclusionRenderer {

    private final Minecraft mc;
    private final RenderGlobal rg;

    private Thread clientThread;

    private ArrayList<WorldRenderer> worldRenderersToUpdateList;

    private double prevRenderX, prevRenderY, prevRenderZ;
    private int cameraStaticTime;

    private short alphaSortProgress = 0;
    private byte frameCounter, frameTarget;

    private int renderersNeedUpdate;

    private boolean resortUpdateList;

    private IRendererUpdateOrderProvider rendererUpdateOrderProvider;
    private List<IRenderGlobalListener> eventListeners;

    /* Make sure other threads can see changes to this */
    private volatile boolean deferNewRenderUpdates;

    public OcclusionRenderer(RenderGlobal renderGlobal) {
        this.rg = renderGlobal;
        this.mc = renderGlobal.mc;
    }

    public RenderGlobal getRenderGlobal() {
        return rg;
    }

    /**
     * If the update list is not queued for a full resort (e.g. when the player moves or renderers have their positions
     * changed), uses binary search to add the renderer in the update queue at the appropriate place. Otherwise,
     * the renderer is just added to the end of the list.
     * @param wr renderer to add to the list
     */
    private void addRendererToUpdateQueue(WorldRenderer wr) {
        if(!((IWorldRenderer)wr).arch$isInUpdateList()) {
            ((IWorldRenderer)wr).arch$setInUpdateList(true);
            if(mc.renderViewEntity == null || resortUpdateList) {
                worldRenderersToUpdateList.add(wr);
                resortUpdateList = true;
                return;
            }
            if(!worldRenderersToUpdateList.isEmpty()) {
                double targetDistance = WorldRendererDistanceHelper.betterDistanceSquared(mc.renderViewEntity, wr);
                int low = 0;
                int high = worldRenderersToUpdateList.size() - 1;
                int finalIndex = -1;
                while(low <= high) {
                    int mid = low + (high - low) / 2;
                    WorldRenderer other = worldRenderersToUpdateList.get(mid);
                    double otherDistance = WorldRendererDistanceHelper.betterDistanceSquared(mc.renderViewEntity, other);
                    if(otherDistance < targetDistance) {
                        low = mid + 1;
                    } else if(otherDistance > targetDistance) {
                        high = mid - 1;
                    } else {
                        finalIndex = mid;
                        break;
                    }
                }
                if(finalIndex == -1)
                    finalIndex = high;
                worldRenderersToUpdateList.add(finalIndex+1, wr);
            } else {
                worldRenderersToUpdateList.add(wr);
            }
        }
    }

    public void handleOffthreadUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
        if(deferNewRenderUpdates || Thread.currentThread() != clientThread) {
            OcclusionHelpers.updateArea(x1, y1, z1, x2, y2, z2);
        } else {
            internalMarkBlockUpdate(x1, y1, z1, x2, y2, z2);
        }
    }

    public void internalMarkBlockUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
        int xStart = MathHelper.bucketInt(x1, 16);
        int yStart = MathHelper.bucketInt(y1, 16);
        int zStart = MathHelper.bucketInt(z1, 16);
        int xEnd = MathHelper.bucketInt(x2, 16);
        int yEnd = MathHelper.bucketInt(y2, 16);
        int zEnd = MathHelper.bucketInt(z2, 16);

        final int width = rg.renderChunksWide;
        final int height = rg.renderChunksTall;
        final int depth = rg.renderChunksDeep;
        final WorldRenderer[] worldRenderers = rg.worldRenderers;
        boolean rebuild = false;

        for (int i = xStart; i <= xEnd; ++i) {
            int x = i % width;
            x += width & (x >> 31);

            for (int j = yStart; j <= yEnd; ++j) {
                int y = j % height;
                y += height & (y >> 31);

                for (int k = zStart; k <= zEnd; ++k) {
                    int z = k % depth;
                    z += depth & (z >> 31);

                    int k4 = (z * height + y) * width + x;
                    WorldRenderer worldrenderer = worldRenderers[k4];

                    if (!worldrenderer.needsUpdate || (worldrenderer.isVisible && !((IWorldRenderer)worldrenderer).arch$isInUpdateList())) {
                        worldrenderer.markDirty();
                        Chunk chunk = rg.theWorld.getChunkFromBlockCoords(worldrenderer.posX, worldrenderer.posZ);
                        if (((ICulledChunk) chunk).getVisibility()[worldrenderer.posY >> 4].isRenderDirty()) {
                            rebuild = true;
                        }
                        addRendererToUpdateQueue(worldrenderer);
                    } else {
                        for(IRenderGlobalListener l : eventListeners) l.onDirtyRendererChanged(worldrenderer);
                    }
                }
            }
        }

        if (rebuild) {
            OcclusionHelpers.worker.dirty = true;
        }
    }

    public boolean skipRenderingIfNotVisible(RenderManager instance, Entity entity, float tick) {
        WorldRenderer rend = getRenderer(entity.posX, entity.posY, entity.posZ);
        if (rend != null && !rend.isVisible) {
            --rg.countEntitiesRendered;
            ++rg.countEntitiesHidden;
            return false;
        }
        return RenderManager.instance.renderEntitySimple(entity, tick);
    }

    public String getDebugInfoRenders() {
        return "C: " + rg.renderersBeingRendered + '/' + rg.renderersLoaded + '/' + rg.worldRenderers.length
            + ". F: " + rg.renderersBeingClipped
            + ", O: " + rg.renderersBeingOccluded
            + ", E: " + rg.renderersSkippingRenderPass
            + ", I: " + rg.dummyRenderInt
            + "; U: " + renderersNeedUpdate
            + ", N: " + rg.worldRenderersToUpdate.size();
    }

    public void initBetterLists() {
        worldRenderersToUpdateList = new ArrayList<>();
        /* Make sure any vanilla code modifying the update queue crashes */
        rg.worldRenderersToUpdate = Collections.unmodifiableList(worldRenderersToUpdateList);
        clientThread = Thread.currentThread();
        rendererUpdateOrderProvider = new DefaultRendererUpdateOrderProvider();
        eventListeners = new ArrayList<>();
    }

    public void clearRendererUpdateQueue(List instance) {
        if(instance == rg.worldRenderersToUpdate) {
            for(WorldRenderer wr : worldRenderersToUpdateList) {
                ((IWorldRenderer)wr).arch$setInUpdateList(false);
            }
            worldRenderersToUpdateList.clear();
        } else {
            throw new AssertionError("Transformer applied to the wrong List.clear method");
        }
    }

    public boolean sortAndAddRendererUpdateQueue(List instance, Object renderer) {
        if(instance == rg.worldRenderersToUpdate) {
            addRendererToUpdateQueue((WorldRenderer)renderer);
            return true;
        } else {
            throw new AssertionError("Transformer applied to the wrong List.clear method");
        }
    }

    private static int fixPos(int pos, int amt) {
        int r = Math.floorDiv(pos, 16) % amt;
        if(r < 0) {
            r += amt;
        }
        return r;
    }

    public WorldRenderer getRenderer(int x, int y, int z) {
        if ((y - 15) > rg.maxBlockY || y < rg.minBlockY || (x - 15) > rg.maxBlockX || x < rg.minBlockX || (z - 15) > rg.maxBlockZ || z < rg.minBlockZ)
            return null;

        x = fixPos(x, rg.renderChunksWide);
        y = fixPos(y, rg.renderChunksTall);
        z = fixPos(z, rg.renderChunksDeep);

        return rg.worldRenderers[(z * rg.renderChunksTall + y) * rg.renderChunksWide + x];
    }

    public WorldRenderer getRenderer(double x, double y, double z) {
        int X = MathHelper.floor_double(x);
        int Y = MathHelper.floor_double(y);
        int Z = MathHelper.floor_double(z);
        return getRenderer(X, Y, Z);
    }

    private boolean rebuildChunks(EntityLivingBase view, long deadline) {
        int updateLimit = deadline == 0 ? 5 : Integer.MAX_VALUE;
        int updates = 0;

        boolean spareTime = true;
        deferNewRenderUpdates = true;
        rendererUpdateOrderProvider.prepare(worldRenderersToUpdateList);
        for (int c = 0; updates < updateLimit && rendererUpdateOrderProvider.hasNext(worldRenderersToUpdateList); ++c) {
            WorldRenderer worldrenderer = rendererUpdateOrderProvider.next(worldRenderersToUpdateList);

            ((IWorldRenderer)worldrenderer).arch$setInUpdateList(false);

            if (!(worldrenderer.isInFrustum & worldrenderer.isVisible) && !OcclusionHelpers.DEBUG_LAZY_CHUNK_UPDATES) {
                continue;
            }

            boolean e = worldrenderer.isWaitingOnOcclusionQuery;
            worldrenderer.updateRenderer(view);
            worldrenderer.isVisible &= !e;
            worldrenderer.isWaitingOnOcclusionQuery = worldrenderer.skipAllRenderPasses() || (mc.theWorld.getChunkFromBlockCoords(worldrenderer.posX, worldrenderer.posZ) instanceof EmptyChunk);
            // can't add fields, re-use

            if(worldrenderer.distanceToEntitySquared(view) > 272f) {
                updates++;
                if(!worldrenderer.isWaitingOnOcclusionQuery || deadline != 0 || OcclusionHelpers.DEBUG_LAZY_CHUNK_UPDATES) {
                    long t = System.nanoTime();
                    if (t > deadline) {
                        spareTime = false;
                        break;
                    }
                }
            }
        }
        rendererUpdateOrderProvider.cleanup(worldRenderersToUpdateList);
        deferNewRenderUpdates = false;
        return spareTime;
    }

    public void performCullingUpdates(EntityLivingBase view, boolean p_72716_2_) {
        rg.theWorld.theProfiler.startSection("deferred_updates");
        while(!OcclusionHelpers.deferredAreas.isEmpty()) {
            OcclusionHelpers.processUpdate(this);
        }
        rg.theWorld.theProfiler.endStartSection("rebuild");

        CameraInfo cam = CameraInfo.getInstance();

        boolean cameraMoved = cam.getEyeX() != prevRenderX || cam.getEyeY() != prevRenderY || cam.getEyeZ() != prevRenderZ;

        prevRenderX = cam.getEyeX();
        prevRenderY = cam.getEyeY();
        prevRenderZ = cam.getEyeZ();

        boolean cameraRotated = PreviousActiveRenderInfo.changed();

        if(!cameraRotated && !cameraMoved) {
            cameraStaticTime++;
        } else {
            cameraStaticTime = 0;
        }

        /*
         * Under certain scenarios (such as renderer.setPosition being called, or the player moving), renderers will]
         * have their distance from the player change. We address that here by sorting the list.
         */
        if(resortUpdateList) {
            worldRenderersToUpdateList.sort(new BasicDistanceSorter(mc.renderViewEntity));
            resortUpdateList = false;
        }
        if (!rg.worldRenderersToUpdate.isEmpty()) {
            ++frameCounter;
            boolean doUpdateAcceleration = cameraStaticTime > 2 && !OcclusionHelpers.DEBUG_LAZY_CHUNK_UPDATES
                    && !OcclusionHelpers.DEBUG_NO_UPDATE_ACCELERATION;
            /* If the camera is not moving, assume a deadline of 30 FPS. */
            rebuildChunks(view, !doUpdateAcceleration ? OcclusionHelpers.chunkUpdateDeadline
                    : mc.entityRenderer.renderEndNanoTime + (1_000_000_000L / 30L));
        }

        rg.theWorld.theProfiler.endStartSection("scan");
//        int yaw = MathHelper.floor_float(view.rotationYaw + 45) >> 4;
//        int pitch = MathHelper.floor_float(view.rotationPitch + 45) >> 4;
        if (OcclusionHelpers.worker.dirty || cameraRotated || OcclusionHelpers.DEBUG_ALWAYS_RUN_OCCLUSION) {
            OcclusionHelpers.worker.run(true);
            PreviousActiveRenderInfo.update();
        }
        rg.theWorld.theProfiler.endSection();
    }

    public void resetLoadedRenderers() {
        if(rg.theWorld != null) {
            rg.renderersLoaded = 0;
        }
    }

    public void resetOcclusionWorker() {
        updateRendererNeighbors();
        if(OcclusionHelpers.worker != null) {
            OcclusionHelpers.worker.dirty = true;
        }
    }

    public void updateRendererNeighbors() {
        if(rg.worldRenderers == null) return;
        for(int i = 0; i < rg.worldRenderers.length; i++) {
            WorldRenderer rend = rg.worldRenderers[i];
            OcclusionWorker.CullInfo ci = ((IWorldRenderer) rend).arch$getCullInfo();
            ci.wrIdx = i;
            Chunk o = rend.worldObj.getChunkFromBlockCoords(rend.posX, rend.posZ);
            VisGraph oSides = isChunkEmpty(o) ? OcclusionWorker.DUMMY : ((ICulledChunk)o).getVisibility()[rend.posY >> 4];
            ci.visGraph = oSides;
            ci.vis = oSides.getVisibilityArray();
            for(EnumFacing dir : EnumFacing.values()) {
                WorldRenderer neighbor = getRenderer(
                        rend.posX + dir.getFrontOffsetX() * 16,
                        rend.posY + dir.getFrontOffsetY() * 16,
                        rend.posZ + dir.getFrontOffsetZ() * 16
                );
                ci.setNeighbor(dir, neighbor == null ? null : ((IWorldRenderer)neighbor).arch$getCullInfo());
            }
        }
    }

    public void pushWorkerRenderer(WorldRenderer wr) {
        if(!(mc.theWorld.getChunkFromBlockCoords(wr.posX, wr.posZ) instanceof EmptyChunk))
            addRendererToUpdateQueue(wr);
    }

    public void markRendererInvisible(WorldRenderer instance) {
        instance.isVisible = false;
        instance.isInFrustum = false;
        instance.markDirty();
    }

    public void setPositionAndMarkInvisible(WorldRenderer wr, int x, int y, int z) {
        wr.setPosition(x, y, z);
        if(((IWorldRenderer)wr).arch$isInUpdateList())
            resortUpdateList = true;
        if(!wr.isInitialized) {
            wr.isWaitingOnOcclusionQuery = false;
            wr.isVisible = false;
        }
    }

    public void runWorker(int x, int y, int z) {
        updateRendererNeighbors();
        OcclusionHelpers.worker.run(true);
    }

    public int sortAndRender(EntityLivingBase view, int pass, double tick) {
        CameraInfo cam = CameraInfo.getInstance();
        cam.update(view, tick);

        rg.theWorld.theProfiler.startSection("sortchunks");

        if (this.mc.gameSettings.renderDistanceChunks != rg.renderDistanceChunks && !(this.mc.currentScreen instanceof GuiVideoSettings)) {
            rg.loadRenderers();
        }

        WorldRenderer[] sortedWorldRenderers = rg.sortedWorldRenderers;
        if (rg.renderersLoaded > 0) {
            int e = rg.renderersLoaded - 10;
            e &= e >> 31;
            e += 10;
            for (int j = 0; j < e; ++j) {
                rg.worldRenderersCheckIndex = (rg.worldRenderersCheckIndex + 1) % rg.renderersLoaded;
                WorldRenderer rend = sortedWorldRenderers[rg.worldRenderersCheckIndex];

                if ((rend.isInFrustum && rend.isVisible) && (rend.needsUpdate || !rend.isInitialized) && !(this.mc.theWorld.getChunkFromBlockCoords(rend.posX, rend.posZ) instanceof EmptyChunk)) {
                    addRendererToUpdateQueue(rend);
                }
            }
        }

        rg.theWorld.theProfiler.startSection("reposition_chunks");
        if (rg.prevChunkSortX != cam.getChunkCoordX() || rg.prevChunkSortY != cam.getChunkCoordY() || rg.prevChunkSortZ != cam.getChunkCoordZ()) {
            rg.prevChunkSortX = cam.getChunkCoordX();
            rg.prevChunkSortY = cam.getChunkCoordY();
            rg.prevChunkSortZ = cam.getChunkCoordZ();
            rg.markRenderersForNewPosition(MathHelper.floor_double(cam.getX()), MathHelper.floor_double(cam.getY()), MathHelper.floor_double(cam.getZ()));
            resortUpdateList = true;
        }
        rg.theWorld.theProfiler.endSection();

        if(pass == 1){
            rg.theWorld.theProfiler.startSection("alpha_sort");
            if(distanceSquared(cam.getX(), cam.getY(), cam.getZ(), rg.prevRenderSortX, rg.prevRenderSortY, rg.prevRenderSortZ) > 1) {
                rg.prevRenderSortX = cam.getX();
                rg.prevRenderSortY = cam.getY();
                rg.prevRenderSortZ = cam.getZ();

                alphaSortProgress = 0;
            }

            int amt = rg.renderersLoaded < 27 ? rg.renderersLoaded : Math.max(rg.renderersLoaded >> 1, 27);
            if (alphaSortProgress < amt) {
                int amountPerFrame = 1;
                for (int i = 0; i < amountPerFrame && alphaSortProgress < amt; ++i) {
                    WorldRenderer r = sortedWorldRenderers[alphaSortProgress++];
                    r.updateRendererSort(view);
                }
            }
            rg.theWorld.theProfiler.endSection();
        }

        rg.theWorld.theProfiler.endStartSection("render");
        RenderHelper.disableStandardItemLighting();
        int k = rg.renderSortedRenderers(0, rg.renderersLoaded, pass, tick);

        rg.theWorld.theProfiler.endSection();
        return k;
    }

    public int sortAndRender(int start, int end, int pass, double tick) {
        CameraInfo cam = CameraInfo.getInstance();

        RenderList[] allRenderLists = rg.allRenderLists;
        for (RenderList allRenderList : allRenderLists) {
            allRenderList.resetList();
        }

        int loopStart = start;
        int loopEnd = end;
        byte dir = 1;

        if (pass == 1) {
            loopStart = end - 1;
            loopEnd = start - 1;
            dir = -1;
        }

        if (pass == 0 && mc.gameSettings.showDebugInfo) {

            mc.theWorld.theProfiler.startSection("debug_info");
            int renderersNotInitialized = 0, renderersBeingClipped = 0, renderersBeingOccluded = 0;
            int renderersBeingRendered = 0, renderersSkippingRenderPass = 0, renderersNeedUpdate = 0;
            WorldRenderer[] worldRenderers = rg.worldRenderers;
            for (WorldRenderer rend : worldRenderers) {
                if (!rend.isInitialized) {
                    ++renderersNotInitialized;
                } else if (!rend.isInFrustum) {
                    ++renderersBeingClipped;
                } else if (!rend.isVisible) {
                    ++renderersBeingOccluded;
                } else if (rend.isWaitingOnOcclusionQuery) {
                    ++renderersSkippingRenderPass;
                } else {
                    ++renderersBeingRendered;
                }
                if (rend.needsUpdate) {
                    ++renderersNeedUpdate;
                }
            }

            rg.dummyRenderInt = renderersNotInitialized;
            rg.renderersBeingClipped = renderersBeingClipped;
            rg.renderersBeingOccluded = renderersBeingOccluded;
            rg.renderersBeingRendered = renderersBeingRendered;
            rg.renderersSkippingRenderPass = renderersSkippingRenderPass;
            this.renderersNeedUpdate = renderersNeedUpdate;
            mc.theWorld.theProfiler.endSection();
        }

        mc.theWorld.theProfiler.startSection("setup_lists");
        int glListsRendered = 0, allRenderListsLength = 0;
        WorldRenderer[] sortedWorldRenderers = rg.sortedWorldRenderers;

        for (int i = loopStart; i != loopEnd; i += dir) {
            WorldRenderer rend = sortedWorldRenderers[i];

            if (rend.isVisible && rend.isInFrustum & !rend.skipRenderPass[pass]) {

                int renderListIndex;

                l: {
                    for (int j = 0; j < allRenderListsLength; ++j) {
                        if (allRenderLists[j].rendersChunk(rend.posXMinus, rend.posYMinus, rend.posZMinus)) {
                            renderListIndex = j;
                            break l;
                        }
                    }
                    renderListIndex = allRenderListsLength++;
                    allRenderLists[renderListIndex].setupRenderList(rend.posXMinus, rend.posYMinus, rend.posZMinus, cam.getEyeX(), cam.getEyeY(), cam.getEyeZ());
                }

                allRenderLists[renderListIndex].addGLRenderList(rend.getGLCallListForPass(pass));
                ++glListsRendered;
            }
        }

        mc.theWorld.theProfiler.endStartSection("call_lists");

        {
            int xSort = MathHelper.floor_double(cam.getX());
            int zSort = MathHelper.floor_double(cam.getZ());
            xSort -= xSort & 1023;
            zSort -= zSort & 1023;
            Arrays.sort(allRenderLists, new RenderDistanceSorter(xSort, zSort));
            rg.renderAllRenderLists(pass, tick);
        }
        mc.theWorld.theProfiler.endSection();

        return glListsRendered;
    }

    public void clipRenderersByFrustum(ICamera cam, float p_72729_2_) {
        for (int i = 0; i < rg.worldRenderers.length; ++i) {
            if((i + rg.frustumCheckOffset & 15) == 0) {
                WorldRenderer wr = rg.worldRenderers[i];
                IWorldRenderer iwr = (IWorldRenderer) wr;
                if (wr.isInFrustum && iwr.arch$getCullInfo().isFrustumCheckPending) {
                    wr.updateInFrustum(cam);
                    iwr.arch$getCullInfo().isFrustumCheckPending = false;
                    if (!wr.isInFrustum) {
                        OcclusionHelpers.worker.dirtyFrustumRenderers++;
                    }
                }
            }
        }

        ++rg.frustumCheckOffset;

        if(rg.frustumCheckOffset % 15 == 0 && OcclusionHelpers.worker.dirtyFrustumRenderers > 0) {
            OcclusionHelpers.worker.dirty = true;
            OcclusionHelpers.worker.dirtyFrustumRenderers = 0;
        }
    }

    public void arch$setRendererUpdateOrderProvider(IRendererUpdateOrderProvider orderProvider) {
        this.rendererUpdateOrderProvider = orderProvider;
    }

    public void arch$addRenderGlobalListener(IRenderGlobalListener listener) {
        this.eventListeners.add(listener);
    }

    private static double distanceSquared(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2);
    }

    private static boolean isChunkEmpty(Chunk chunk) {
        return chunk == null || chunk.isEmpty();
    }
}
