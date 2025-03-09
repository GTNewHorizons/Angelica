package org.embeddedt.embeddium.impl.render.chunk.occlusion;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import org.embeddedt.embeddium.impl.common.util.MathUtil;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.util.PositionUtil;
import org.embeddedt.embeddium.impl.util.collections.DoubleBufferedQueue;
import org.embeddedt.embeddium.impl.util.collections.ReadQueue;
import org.embeddedt.embeddium.impl.util.collections.WriteQueue;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3ic;

import java.util.Objects;

public class OcclusionCuller {
    private final Long2ReferenceMap<OcclusionNode> sections;
    private final int minSectionY, maxSectionY;

    private final DoubleBufferedQueue<OcclusionNode> queue = new DoubleBufferedQueue<>();

    private boolean isCameraInUnloadedSection;

    public OcclusionCuller(Long2ReferenceMap<OcclusionNode> sections, int minSectionY, int maxSectionY) {
        this.sections = sections;
        this.minSectionY = minSectionY;
        this.maxSectionY = maxSectionY;
    }

    public void findVisible(Visitor visitor,
                            Viewport viewport,
                            float searchDistance,
                            boolean useOcclusionCulling,
                            int frame)
    {
        final var queues = this.queue;
        queues.reset();

        this.isCameraInUnloadedSection = false;
        this.init(visitor, queues.write(), viewport, searchDistance, useOcclusionCulling, frame);
        if(this.isCameraInUnloadedSection) {
            useOcclusionCulling = false;
        }

        while (queues.flip()) {
            processQueue(visitor, viewport, searchDistance, useOcclusionCulling, frame, queues.read(), queues.write());
        }
    }

    private static void processQueue(Visitor visitor,
                                     Viewport viewport,
                                     float searchDistance,
                                     boolean useOcclusionCulling,
                                     int frame,
                                     ReadQueue<OcclusionNode> readQueue,
                                     WriteQueue<OcclusionNode> writeQueue)
    {
        OcclusionNode section;

        while ((section = readQueue.dequeue()) != null) {
            boolean visible = isSectionVisible(section, viewport, searchDistance);
            visitor.visit(section, visible);

            if (!visible) {
                continue;
            }

            int connections;

            {
                if (useOcclusionCulling) {
                    // When using occlusion culling, we can only traverse into neighbors for which there is a path of
                    // visibility through this chunk. This is determined by taking all the incoming paths to this chunk and
                    // creating a union of the outgoing paths from those.
                    connections = VisibilityEncoding.getConnections(section.getVisibilityData(), section.getIncomingDirections());
                } else {
                    // Not using any occlusion culling, so traversing in any direction is legal.
                    connections = GraphDirectionSet.ALL;
                }

                // We can only traverse *outwards* from the center of the graph search, so mask off any invalid
                // directions.
                connections &= getOutwardDirections(viewport.getChunkCoord(), section);
            }

            visitNeighbors(writeQueue, section, connections, frame);
        }
    }

    private static boolean isSectionVisible(OcclusionNode section, Viewport viewport, float maxDistance) {
        return isWithinRenderDistance(viewport.getTransform(), section, maxDistance) && isWithinFrustum(viewport, section);
    }

    private static void visitNeighbors(final WriteQueue<OcclusionNode> queue, OcclusionNode section, int outgoing, int frame) {
        // Only traverse into neighbors which are actually present.
        // This avoids a null-check on each invocation to enqueue, and since the compiler will see that a null
        // is never encountered (after profiling), it will optimize it away.
        outgoing &= section.getAdjacentMask();

        // Check if there are any valid connections left, and if not, early-exit.
        if (outgoing == GraphDirectionSet.NONE) {
            return;
        }

        // This helps the compiler move the checks for some invariants upwards.
        queue.ensureCapacity(6);

        if (GraphDirectionSet.contains(outgoing, GraphDirection.DOWN)) {
            visitNode(queue, section.adjacentDown, GraphDirectionSet.of(GraphDirection.UP), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.UP)) {
            visitNode(queue, section.adjacentUp, GraphDirectionSet.of(GraphDirection.DOWN), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.NORTH)) {
            visitNode(queue, section.adjacentNorth, GraphDirectionSet.of(GraphDirection.SOUTH), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.SOUTH)) {
            visitNode(queue, section.adjacentSouth, GraphDirectionSet.of(GraphDirection.NORTH), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.WEST)) {
            visitNode(queue, section.adjacentWest, GraphDirectionSet.of(GraphDirection.EAST), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.EAST)) {
            visitNode(queue, section.adjacentEast, GraphDirectionSet.of(GraphDirection.WEST), frame);
        }
    }

    private static void visitNode(final WriteQueue<OcclusionNode> queue, @NotNull OcclusionNode render, int incoming, int frame) {
        if (render.getLastVisibleFrame() != frame) {
            // This is the first time we are visiting this section during the given frame, so we must
            // reset the state.
            render.setLastVisibleFrame(frame);
            render.setIncomingDirections(GraphDirectionSet.NONE);

            queue.enqueue(render);
        }

        render.addIncomingDirections(incoming);
    }

    private static int getOutwardDirections(Vector3ic origin, OcclusionNode section) {
        int planes = 0;

        planes |= section.getChunkX() <= origin.x() ? 1 << GraphDirection.WEST  : 0;
        planes |= section.getChunkX() >= origin.x() ? 1 << GraphDirection.EAST  : 0;

        planes |= section.getChunkY() <= origin.y() ? 1 << GraphDirection.DOWN  : 0;
        planes |= section.getChunkY() >= origin.y() ? 1 << GraphDirection.UP    : 0;

        planes |= section.getChunkZ() <= origin.z() ? 1 << GraphDirection.NORTH : 0;
        planes |= section.getChunkZ() >= origin.z() ? 1 << GraphDirection.SOUTH : 0;

        return planes;
    }

    private static boolean isWithinRenderDistance(CameraTransform camera, OcclusionNode section, float maxDistance) {
        // origin point of the chunk's bounding box (in view space)
        int ox = section.getOriginX() - camera.intX;
        int oy = section.getOriginY() - camera.intY;
        int oz = section.getOriginZ() - camera.intZ;

        // coordinates of the point to compare (in view space)
        // this is the closest point within the bounding box to the center (0, 0, 0)
        float dx = nearestToZero(ox, ox + 16) - camera.fracX;
        float dy = nearestToZero(oy, oy + 16) - camera.fracY;
        float dz = nearestToZero(oz, oz + 16) - camera.fracZ;

        return ((((dx * dx) + (dz * dz)) < (maxDistance * maxDistance)) && (Math.abs(dy) < maxDistance));
        //return DistanceFilterHolder.INSTANCE.isWithinDistance(dx, dy, dz, maxDistance);
    }

    @SuppressWarnings("ManualMinMaxCalculation") // we know what we are doing.
    private static int nearestToZero(int min, int max) {
        // this compiles to slightly better code than Math.min(Math.max(0, min), max)
        int clamped = 0;
        if (min > 0) { clamped = min; }
        if (max < 0) { clamped = max; }
        return clamped;
    }

    // The bounding box of a chunk section must be large enough to contain all possible geometry within it. Block models
    // can extend outside a block volume by +/- 1.0 blocks on all axis. Additionally, we make use of a small epsilon
    // to deal with floating point imprecision during a frustum check (see GH#2132).
    private static final float CHUNK_SECTION_SIZE = 8.0f /* chunk bounds */ + 1.0f /* maximum model extent */ + 0.125f /* epsilon */;

    public static boolean isWithinFrustum(Viewport viewport, OcclusionNode section) {
        return viewport.isBoxVisible(section.getCenterX(), section.getCenterY(), section.getCenterZ(), CHUNK_SECTION_SIZE);
    }

    private void init(Visitor visitor,
                      WriteQueue<OcclusionNode> queue,
                      Viewport viewport,
                      float searchDistance,
                      boolean useOcclusionCulling,
                      int frame)
    {
        var origin = viewport.getChunkCoord();

        if (origin.y() < this.minSectionY) {
            // below the world
            this.initOutsideWorldHeight(queue, viewport, searchDistance, frame,
                    this.minSectionY, GraphDirectionSet.of(GraphDirection.DOWN));
        } else if (origin.y() >= this.maxSectionY) {
            // above the world
            this.initOutsideWorldHeight(queue, viewport, searchDistance, frame,
                    this.maxSectionY - 1, GraphDirectionSet.of(GraphDirection.UP));
        } else if(this.getRenderSection(origin.x(), origin.y(), origin.z()) == null) {
            // inside the world height-wise, but in an unloaded section
            this.initOutsideWorldHeight(queue, viewport, searchDistance, frame,
                    origin.y(), GraphDirectionSet.of(GraphDirection.UP) | GraphDirectionSet.of(GraphDirection.DOWN));
            this.isCameraInUnloadedSection = true;
        } else {
            this.initWithinWorld(visitor, queue, viewport, useOcclusionCulling, frame);
        }
    }

    private void initWithinWorld(Visitor visitor, WriteQueue<OcclusionNode> queue, Viewport viewport, boolean useOcclusionCulling, int frame) {
        var origin = viewport.getChunkCoord();
        var section = this.getRenderSection(origin.x(), origin.y(), origin.z());

        Objects.requireNonNull(section);

        section.setLastVisibleFrame(frame);
        section.setIncomingDirections(GraphDirectionSet.NONE);

        visitor.visit(section, true);

        int outgoing;

        if (useOcclusionCulling) {
            // Since the camera is located inside this chunk, there are no "incoming" directions. So we need to instead
            // find any possible paths out of this chunk and enqueue those neighbors.
            outgoing = VisibilityEncoding.getConnections(section.getVisibilityData());
        } else {
            // Occlusion culling is disabled, so we can traverse into any neighbor.
            outgoing = GraphDirectionSet.ALL;
        }

        visitNeighbors(queue, section, outgoing, frame);
    }

    // Enqueues sections that are inside the viewport using diamond spiral iteration to avoid sorting and ensure a
    // consistent order. Innermost layers are enqueued first. Within each layer, iteration starts at the northernmost
    // section and proceeds counterclockwise (N->W->S->E).
    private void initOutsideWorldHeight(WriteQueue<OcclusionNode> queue,
                                        Viewport viewport,
                                        float searchDistance,
                                        int frame,
                                        int height,
                                        int direction)
    {
        var origin = viewport.getChunkCoord();
        var radius = MathUtil.mojfloor(searchDistance / 16.0f);

        // Layer 0
        this.tryVisitNode(queue, origin.x(), height, origin.z(), direction, frame, viewport);

        // Complete layers, excluding layer 0
        for (int layer = 1; layer <= radius; layer++) {
            for (int z = -layer; z < layer; z++) {
                int x = Math.abs(z) - layer;
                this.tryVisitNode(queue, origin.x() + x, height, origin.z() + z, direction, frame, viewport);
            }

            for (int z = layer; z > -layer; z--) {
                int x = layer - Math.abs(z);
                this.tryVisitNode(queue, origin.x() + x, height, origin.z() + z, direction, frame, viewport);
            }
        }

        // Incomplete layers
        for (int layer = radius + 1; layer <= 2 * radius; layer++) {
            int l = layer - radius;

            for (int z = -radius; z <= -l; z++) {
                int x = -z - layer;
                this.tryVisitNode(queue, origin.x() + x, height, origin.z() + z, direction, frame, viewport);
            }

            for (int z = l; z <= radius; z++) {
                int x = z - layer;
                this.tryVisitNode(queue, origin.x() + x, height, origin.z() + z, direction, frame, viewport);
            }

            for (int z = radius; z >= l; z--) {
                int x = layer - z;
                this.tryVisitNode(queue, origin.x() + x, height, origin.z() + z, direction, frame, viewport);
            }

            for (int z = -l; z >= -radius; z--) {
                int x = layer + z;
                this.tryVisitNode(queue, origin.x() + x, height, origin.z() + z, direction, frame, viewport);
            }
        }
    }

    private void tryVisitNode(WriteQueue<OcclusionNode> queue, int x, int y, int z, int direction, int frame, Viewport viewport) {
        OcclusionNode section = this.getRenderSection(x, y, z);

        if (section == null || !isWithinFrustum(viewport, section)) {
            return;
        }

        visitNode(queue, section, direction, frame);
    }

    private OcclusionNode getRenderSection(int x, int y, int z) {
        return this.sections.get(PositionUtil.packSection(x, y, z));
    }

    public interface Visitor {
        void visit(OcclusionNode section, boolean visible);
    }

    /*
    private static class DistanceFilterHolder {
        private static final RenderSectionDistanceFilter INSTANCE;

        static {
            var event = new RenderSectionDistanceFilterEvent();
            RenderSectionDistanceFilterEvent.BUS.post(event);
            INSTANCE = event.getFilter();
        }
    }

     */
}
