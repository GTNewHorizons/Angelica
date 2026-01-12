package me.jellysquid.mods.sodium.client.render.chunk.cull.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.compat.mojang.ChunkOcclusionData;
import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.jellysquid.mods.sodium.client.render.chunk.cull.ChunkCuller;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;

public class ChunkGraphCuller implements ChunkCuller {
    private final Long2ObjectMap<ChunkGraphNode> nodes = new Long2ObjectOpenHashMap<>();

    private final ChunkGraphIterationQueue visible = new ChunkGraphIterationQueue();
    private final World world;
    private final int renderDistance;

    private FrustumExtended frustum;
    private boolean useOcclusionCulling;

    private int activeFrame = 0;
    private int centerChunkX, centerChunkZ;

    public ChunkGraphCuller(World world, int renderDistance) {
        this.world = world;
        this.renderDistance = renderDistance;
    }

    @Override
    public IntArrayList computeVisible(Camera camera, FrustumExtended frustum, int frame, boolean spectator) {
        this.initSearch(camera, frustum, frame, spectator);

        ChunkGraphIterationQueue queue = this.visible;

        for (int i = 0; i < queue.size(); i++) {
            ChunkGraphNode node = queue.getNode(i);
            short cullData = node.computeQueuePop();

            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                if (useOcclusionCulling && (cullData & (1 << dir.ordinal())) == 0) {
                    continue;
                }

                ChunkGraphNode adj = node.getConnectedNode(dir);

                if (adj != null && this.isWithinRenderDistance(adj)) {
                    this.bfsEnqueue(node, adj, dir.getOpposite(), cullData);
                }
            }
        }

        return this.visible.getOrderedIdList();
    }

    private boolean isWithinRenderDistance(ChunkGraphNode adj) {
        int x = Math.abs(adj.getChunkX() - this.centerChunkX);
        int z = Math.abs(adj.getChunkZ() - this.centerChunkZ);

        return x <= this.renderDistance && z <= this.renderDistance;
    }

    private void initSearch(Camera camera, FrustumExtended frustum, int frame, boolean spectator) {
        this.activeFrame = frame;
        this.frustum = frustum;
        this.useOcclusionCulling = true;

        this.visible.clear();

        final BlockPos origin = camera.getBlockPos();

        final int chunkX = origin.getX() >> 4;
        int chunkY = origin.getY() >> 4;
        final int chunkZ = origin.getZ() >> 4;

        this.centerChunkX = chunkX;
        this.centerChunkZ = chunkZ;

        final ChunkGraphNode rootNode = this.getNode(chunkX, chunkY, chunkZ);

        if (rootNode != null) {
            rootNode.resetCullingState();
            rootNode.setLastVisibleFrame(frame);

            if (spectator) {
                final Block block = this.world.getBlock(origin.getX(), origin.getY(), origin.getZ());
                if(block.isOpaqueCube()) {
                    this.useOcclusionCulling = false;
                }
            }

            this.visible.add(rootNode);
        } else {
            chunkY = origin.getY() >> 4;

            if (!ModStatus.isCubicChunksLoaded) {
                chunkY = MathHelper.clamp_int(chunkY, 0, 15);
            }

            final List<ChunkGraphNode> bestNodes = new ArrayList<>();

            for (int x2 = -this.renderDistance; x2 <= this.renderDistance; ++x2) {
                for (int z2 = -this.renderDistance; z2 <= this.renderDistance; ++z2) {
                    final ChunkGraphNode node = this.getNode(chunkX + x2, chunkY, chunkZ + z2);

                    if (node == null || node.isCulledByFrustum(frustum)) {
                        continue;
                    }

                    node.resetCullingState();
                    node.setLastVisibleFrame(frame);

                    bestNodes.add(node);
                }
            }

            bestNodes.sort(Comparator.comparingDouble(node -> node.getSquaredDistance(origin)));

            for (ChunkGraphNode node : bestNodes) {
                this.visible.add(node);
            }
        }
    }


    private void bfsEnqueue(ChunkGraphNode parent, ChunkGraphNode node, ForgeDirection flow, short parentalData) {
        if (node.getLastVisibleFrame() == this.activeFrame) {
            node.updateCullingState(flow, parentalData);
            return;
        }
        node.setLastVisibleFrame(this.activeFrame);

        if (node.isCulledByFrustum(this.frustum)) {
            return;
        }

        node.setCullingState(parentalData);
        node.updateCullingState(flow, parentalData);

        this.visible.add(node);
    }

    private void connectNeighborNodes(ChunkGraphNode node) {
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            final ChunkGraphNode adj = this.findAdjacentNode(node, dir);

            if (adj != null) {
                adj.setAdjacentNode(dir.getOpposite(), node);
            }

            node.setAdjacentNode(dir, adj);
        }
    }

    private void disconnectNeighborNodes(ChunkGraphNode node) {
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            final ChunkGraphNode adj = node.getConnectedNode(dir);

            if (adj != null) {
                adj.setAdjacentNode(dir.getOpposite(), null);
            }

            node.setAdjacentNode(dir, null);
        }
    }

    private ChunkGraphNode findAdjacentNode(ChunkGraphNode node, ForgeDirection dir) {
        return this.getNode(node.getChunkX() + dir.offsetX, node.getChunkY() + dir.offsetY, node.getChunkZ() + dir.offsetZ);
    }

    private ChunkGraphNode getNode(int x, int y, int z) {
        return this.nodes.get(ChunkSectionPos.asLong(x, y, z));
    }

    @Override
    public void onSectionStateChanged(int x, int y, int z, ChunkOcclusionData occlusionData) {
        ChunkGraphNode node = this.getNode(x, y, z);

        if (node != null) {
            node.setOcclusionData(occlusionData);
        }
    }

    @Override
    public void onSectionLoaded(int x, int y, int z, int id) {
        final ChunkGraphNode node = new ChunkGraphNode(x, y, z, id);
        final ChunkGraphNode prev;

        if ((prev = this.nodes.put(ChunkSectionPos.asLong(x, y, z), node)) != null) {
            this.disconnectNeighborNodes(prev);
        }

        this.connectNeighborNodes(node);
    }

    @Override
    public void onSectionUnloaded(int x, int y, int z) {
        final ChunkGraphNode node = this.nodes.remove(ChunkSectionPos.asLong(x, y, z));

        if (node != null) {
            this.disconnectNeighborNodes(node);
        }
    }

    @Override
    public boolean isSectionVisible(int x, int y, int z) {
        final ChunkGraphNode render = this.getNode(x, y, z);

        if (render == null) {
            return false;
        }

        return render.getLastVisibleFrame() == this.activeFrame;
    }
}
