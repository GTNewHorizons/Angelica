package org.embeddedt.archaicfix.occlusion;

import static org.embeddedt.archaicfix.occlusion.OcclusionHelpers.DEBUG_PRINT_QUEUE_ITERATIONS;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class OcclusionWorker {

    public OcclusionWorker() {

        /*for (int i = 0; i < fStack.length; ++i) {
            fStack[i] = new Frustrum();
        }//*/
    }

    public void setWorld(RenderGlobal rg, WorldClient world) {
        theWorld = world;
    }

    public static final VisGraph DUMMY = new VisGraph();

    static {
        DUMMY.computeVisibility();
    }

    public volatile boolean dirty = false;
    public int dirtyFrustumRenderers;
    private int frame = 0;
    private final List<CullInfo> queue = new ArrayList<>();
    @SuppressWarnings("unused")
    private Frustrum fStack = new Frustrum();
    private WorldClient theWorld;

    /**
     * We cache the values of WorldRenderer#isInFrustum here to avoid the overhead of accessing the field.
     */
    private boolean[] isWRInFrustum;

    private final Minecraft mc = Minecraft.getMinecraft();

    private RenderGlobal getRender() {
        return getExtendedRender().getRenderGlobal();
    }
    
    private OcclusionRenderer getExtendedRender() {
        return OcclusionHelpers.renderer;
    }
    
    public void run(boolean immediate) {
        frame++;
        queue.clear();
        int queueIterations = 0;

        if (getRender() == null) {
            return;
        }
        EntityLivingBase view = mc.renderViewEntity;
        if (theWorld == null || view == null) {
            return;
        }
        long t0 = DEBUG_PRINT_QUEUE_ITERATIONS ? System.nanoTime() : 0;

        Frustrum frustum = getFrustum();

        theWorld.theProfiler.startSection("prep");

        prepareRenderers();

        seedQueue(frustum);

        long t1 = DEBUG_PRINT_QUEUE_ITERATIONS ? System.nanoTime() : 0;

        theWorld.theProfiler.endStartSection("process_queue");
        for (int i = 0; i < queue.size(); i++) {
            queueIterations++;
            CullInfo ci = queue.get(i);

            for (StepDirection stepPos : CullInfo.ALLOWED_STEPS[ci.facings & 0b111111]) {
                if (canStep(ci, stepPos)) {
                    maybeEnqueueNeighbor(ci, stepPos, queue, frustum);
                }
            }
        }
        theWorld.theProfiler.endStartSection("cleanup");

        long t2 = DEBUG_PRINT_QUEUE_ITERATIONS ? System.nanoTime() : 0;

        for (CullInfo ci : queue) {
            markRenderer(ci, view);
        }

        if (DEBUG_PRINT_QUEUE_ITERATIONS) {
            if (queueIterations != 0) {
                System.out.println("queue iterations: " + queueIterations);
            }
            long t3 = System.nanoTime();
            System.out.println(((t1 - t0) / 1000000.0) + " ms prepare + " + (t2 - t1) / 1000000.0 + " ms queue + " + (t3 - t2) / 1000000.0 + " ms mark");
        }

        dirty = false;
        queue.clear();
        theWorld.theProfiler.endSection();
    }

    private void prepareRenderers() {
        RenderGlobal render = getRender();
        if (isWRInFrustum == null || isWRInFrustum.length != render.worldRenderers.length) {
            isWRInFrustum = new boolean[render.worldRenderers.length];
        }

        for (int i = 0; i < render.worldRenderers.length; ++i) {
            WorldRenderer wr = render.worldRenderers[i];
            wr.isVisible = false;
            isWRInFrustum[i] = wr.isInFrustum;
        }
        render.renderersLoaded = 0;
    }

    private Frustrum getFrustum() {
        EntityLivingBase view = mc.renderViewEntity;

        Frustrum frustum = new Frustrum();
        // TODO: interpolate using partial tick time
        frustum.setPosition(view.posX, view.posY, view.posZ);
        return frustum;
    }

    private void seedQueue(Frustrum frustum) {
        CameraInfo cam = CameraInfo.getInstance();

        int viewX = MathHelper.floor_double(cam.getX());
        int viewY = MathHelper.floor_double(cam.getY());
        int viewZ = MathHelper.floor_double(cam.getZ());

        theWorld.theProfiler.endStartSection("gather_chunks");

        OcclusionRenderer extendedRender = getExtendedRender();

        theWorld.theProfiler.endStartSection("seed_queue");

        WorldRenderer center = extendedRender.getRenderer(viewX, viewY, viewZ);
        if (center != null) {
            CullInfo ci = ((IWorldRenderer) center).arch$getCullInfo();
            isInFrustum(ci, frustum); // make sure frustum status gets updated for the starting renderer
            ci.init(StepDirection.NONE, (byte) 0);
            queue.add(ci);
        } else {
            int level = viewY > 5 ? 250 : 5;
            center = extendedRender.getRenderer(viewX, level, viewZ);
            if (center != null) {
                StepDirection pos = viewY < 5 ? StepDirection.UP : StepDirection.DOWN;
                {
                    CullInfo ci = ((IWorldRenderer) center).arch$getCullInfo();
                    ci.init(StepDirection.NONE, (byte) 0);
                    queue.add(ci);
                }
                boolean allNull = false;
                theWorld.theProfiler.startSection("gather_world");
                for (int size = 1; !allNull; ++size) {
                    allNull = true;
                    for (int i = 0, j = size; i < size; ) {
                        for (int k = 0; k < 4; ++k) {
                            int xm = (k & 1) == 0 ? -1 : 1;
                            int zm = (k & 2) == 0 ? -1 : 1;
                            center = extendedRender.getRenderer(viewX + i * 16 * xm, level, viewZ + j * 16 * zm);
                            if (center != null) {
                                CullInfo ci = ((IWorldRenderer) center).arch$getCullInfo();
                                if (isInFrustum(ci, frustum)) {
                                    allNull = false;
                                    ci.init(StepDirection.NONE, (byte) 0);
                                    queue.add(ci);
                                }
                            }
                        }
                        ++i;
                        --j;
                    }
                }
                theWorld.theProfiler.endSection();
            }
        }
    }

    private boolean canStep(CullInfo info, StepDirection stepPos) {
        boolean allVis = mc.playerController.currentGameType.getID() == 3;

        if (!allVis && !SetVisibility.isVisible(info.vis[0], info.dir.getOpposite().facing, stepPos.facing)) {
            return false;
        }

        return true;
    }

    private void maybeEnqueueNeighbor(CullInfo info, StepDirection stepPos, Collection<CullInfo> queue, Frustrum frustum) {
        CullInfo neighbor = info.getNeighbor(stepPos.facing);

        if (neighbor == null || !neighbor.setLastCullUpdateFrame(frame) || !isInFrustum(neighbor, frustum))
            return;

        neighbor.init(stepPos, info.facings);

        queue.add(neighbor);
    }

    private void markRenderer(CullInfo info, EntityLivingBase view) {
        RenderGlobal render = getRender();
        WorldRenderer rend = render.worldRenderers[info.wrIdx];
        if (!rend.isVisible) {
            rend.isVisible = true;
            if (!rend.isWaitingOnOcclusionQuery) {
                // only add it to the list of sorted renderers if it's not skipping all passes (re-used field)
                render.sortedWorldRenderers[render.renderersLoaded++] = rend;
            }
        }
        if (rend.needsUpdate || !rend.isInitialized || info.visGraph.isRenderDirty()) {
            rend.needsUpdate = true;
            if (!rend.isInitialized || (rend.needsUpdate && rend.distanceToEntitySquared(view) <= 1128.0F)) {
                getExtendedRender().pushWorkerRenderer(rend);
            }
        }
    }

    private boolean isInFrustum(CullInfo ci, Frustrum frustum) {
        if (isWRInFrustum[ci.wrIdx]) {
            ci.isFrustumCheckPending = true;
        } else {
            WorldRenderer wr = Minecraft.getMinecraft().renderGlobal.worldRenderers[ci.wrIdx];
            wr.updateInFrustum(frustum);
            isWRInFrustum[ci.wrIdx] = wr.isInFrustum;
        }
        return isWRInFrustum[ci.wrIdx];
    }

    public enum StepDirection {
        // EnumFacing.EAST and EnumFacing.WEST is flipped in MCP
        DOWN(EnumFacing.DOWN, 0, -1, 0),
        UP(EnumFacing.UP, 0, 16, 0),
        WEST(EnumFacing.EAST /* WEST */, -1, 0, 0),
        EAST(EnumFacing.WEST /* EAST */, 16, 0, 0),
        NORTH(EnumFacing.NORTH, 0, 0, -1),
        SOUTH(EnumFacing.SOUTH, 0, 0, 16),
        NONE(null, 0, 0, 0),
        NONE_opp(null, 0, 0, 0);

        public static final StepDirection[] DIRECTIONS = values();
        public static final StepDirection[][] DIRECTIONS_BIAS = new StepDirection[6][6];
        public static final StepDirection[] FROM_FACING = new StepDirection[6];
        public static final List<StepDirection> SIDES = Arrays.asList(DIRECTIONS).subList(1, 6);
        static {
            for (int i = 0; i < 6; ++i) {
                StepDirection pos = DIRECTIONS[i];
                FROM_FACING[pos.facing.ordinal()] = pos;
                StepDirection[] bias = DIRECTIONS_BIAS[i];
                int j = 0, xor = pos.ordinal() & 1;
                switch (pos) {
                    case DOWN:
                    case UP:
                        bias[j++] = pos;
                        bias[j++] = DIRECTIONS[NORTH.ordinal() ^ xor];
                        bias[j++] = DIRECTIONS[SOUTH.ordinal() ^ xor];
                        bias[j++] = DIRECTIONS[EAST.ordinal() ^ xor];
                        bias[j++] = DIRECTIONS[WEST.ordinal() ^ xor];
                        bias[j++] = pos.getOpposite();
                        break;
                    case WEST:
                    case EAST:
                        bias[j++] = pos;
                        bias[j++] = DIRECTIONS[NORTH.ordinal() ^ xor];
                        bias[j++] = DIRECTIONS[SOUTH.ordinal() ^ xor];
                        bias[j++] = DIRECTIONS[UP.ordinal() ^ xor];
                        bias[j++] = DIRECTIONS[DOWN.ordinal() ^ xor];
                        bias[j++] = pos.getOpposite();
                        break;
                    case NORTH:
                    case SOUTH:
                        bias[j++] = pos;
                        bias[j++] = DIRECTIONS[EAST.ordinal() ^ xor];
                        bias[j++] = DIRECTIONS[WEST.ordinal() ^ xor];
                        bias[j++] = DIRECTIONS[UP.ordinal() ^ xor];
                        bias[j++] = DIRECTIONS[DOWN.ordinal() ^ xor];
                        bias[j++] = pos.getOpposite();
                        break;
                    case NONE:
                    case NONE_opp:
                        break;
                }
            }
        }

        public final int x, y, z;
        public final EnumFacing facing;

        StepDirection(EnumFacing face, int x, int y, int z) {

            this.facing = face;
            this.x = x;
            this.y = y;
            this.z = z;
            _x = x > 0 ? 1 : x;
            _y = y > 0 ? 1 : y;
            _z = z > 0 ? 1 : z;
        }

        public StepDirection getOpposite() {

            return DIRECTIONS[ordinal() ^ 1];
        }

        private final int _x, _y, _z;
    }

    public static class CullInfo {

        public static final StepDirection[][] ALLOWED_STEPS;

        static {
            ALLOWED_STEPS = generateAllowedSteps();
        }

        private static StepDirection[][] generateAllowedSteps() {
            StepDirection[][] allowedSteps = new StepDirection[(int) Math.pow(2, 6) + 1][];

            for (int xStep = -1; xStep <= 1; xStep++) {
                for (int yStep = -1; yStep <= 1; yStep++) {
                    for (int zStep = -1; zStep <= 1; zStep++) {
                        byte mask = 0;

                        //                    SNEWUD
                        mask |= new byte[]{0b000100,
                                0b000000,
                                0b001000
                        }[xStep + 1];

                        //                    SNEWUD
                        mask |= new byte[]{0b000001,
                                0b000000,
                                0b000010
                        }[yStep + 1];

                        //                    SNEWUD
                        mask |= new byte[]{0b010000,
                                0b000000,
                                0b100000
                        }[zStep + 1];

                        byte finalMask = mask;
                        allowedSteps[mask] =
                                Stream.of(StepDirection.DOWN, StepDirection.UP, StepDirection.NORTH, StepDirection.SOUTH, StepDirection.WEST, StepDirection.EAST)
                                        .filter(p -> (1 << (p.getOpposite().ordinal()) & finalMask) == 0)
                                        .toArray(StepDirection[]::new);
                    }
                }
            }
            return allowedSteps;
        }

        /**
         * The index of the world renderer in RenderGlobal#worldRenderers. Not stored as a reference because I
         * found that having it slows things down significantly.
         */
        public int wrIdx;
        public CullInfo[] neighbors;
        /**
         * A direct reference to the visibility graph's visibility mask, used to avoid the significant overhead of
         * accessing VisGraph's fields.
         */
        public long[] vis;
        public VisGraph visGraph;
        /**
         * The direction we stepped in to reach this subchunk.
         */
        public StepDirection dir;
        /**
         * All the directions we have stepped in to reach this subchunk.
         */
        public byte facings;
        public int lastCullUpdateFrame;
        public boolean isFrustumCheckPending;

        public CullInfo() {
            this.neighbors = new CullInfo[EnumFacing.values().length];
            this.visGraph = DUMMY;
            this.vis = visGraph.getVisibilityArray();
        }

        public CullInfo init(StepDirection dir, byte facings) {
            this.dir = dir;

            this.facings = facings;
            this.facings |= (1 << dir.ordinal());

            return this;
        }

        public CullInfo getNeighbor(EnumFacing dir) {
            return neighbors[dir.ordinal()];
        }

        public void setNeighbor(EnumFacing dir, CullInfo neighbor) {
            neighbors[dir.ordinal()] = neighbor;
        }

        /**
         * Sets the number of the last frame when this renderer was visited by the occlusion culling algorithm.
         * Returns true if the value was changed as a result.
         */
        public boolean setLastCullUpdateFrame(int lastCullUpdateFrame) {
            if (this.lastCullUpdateFrame == lastCullUpdateFrame) return false;
            this.lastCullUpdateFrame = lastCullUpdateFrame;
            return true;
        }
    }
}
