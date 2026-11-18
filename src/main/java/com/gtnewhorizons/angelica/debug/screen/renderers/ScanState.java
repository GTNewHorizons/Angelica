package com.gtnewhorizons.angelica.debug.screen.renderers;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

final class ScanState implements CubeWalker.Sink {

    interface CellVisitor {

        boolean visitCell(ScanState scan, DebugMeshData mesh, int x, int y, int z);
    }

    private final DebugWorldChanges changes;
    private final Chunk[] chunks = new Chunk[4];
    private final int[] chunkX = new int[4];
    private final int[] chunkZ = new int[4];
    private Chunk column;
    private int columnX;
    private int columnZ;
    private World world;
    private EntityLivingBase viewer;
    private CellVisitor visitor;
    private DebugMeshData mesh;
    private int budget;
    private int viewerX;
    private int viewerY;
    private int viewerZ;
    int slot;

    ScanState(DebugWorldChanges changes) {
        this.changes = changes;
    }

    void begin(World world, EntityLivingBase viewer) {
        this.world = world;
        this.viewer = viewer;
        this.viewerX = MathHelper.floor_double(viewer.posX);
        this.viewerY = MathHelper.floor_double(viewer.posY);
        this.viewerZ = MathHelper.floor_double(viewer.posZ);
    }

    void end() {
        world = null;
        viewer = null;
        visitor = null;
        mesh = null;
        column = null;
        chunks[0] = null;
        chunks[1] = null;
        chunks[2] = null;
        chunks[3] = null;
    }

    private Chunk chunk(int x, int z) {
        if (column != null && x == columnX && z == columnZ) return column;
        final int cx = x >> 4;
        final int cz = z >> 4;
        final int i = (cx & 1) << 1 | (cz & 1);
        Chunk chunk = chunks[i];
        if (chunk == null || chunkX[i] != cx || chunkZ[i] != cz) {
            chunk = world.getChunkFromChunkCoords(cx, cz);
            chunks[i] = chunk;
            chunkX[i] = cx;
            chunkZ[i] = cz;
        }
        return chunk;
    }

    World world() {
        return world;
    }

    EntityLivingBase viewer() {
        return viewer;
    }

    int viewerX() {
        return viewerX;
    }

    int viewerY() {
        return viewerY;
    }

    int viewerZ() {
        return viewerZ;
    }

    boolean isLoaded(int x, int z) {
        return !chunk(x, z).isEmpty();
    }

    Block getBlock(int x, int y, int z) {
        if (y < 0 || y > 255) return Blocks.air;
        final Chunk chunk = chunk(x, z);
        if (chunk.isEmpty()) return Blocks.air;
        return chunk.getBlock(x & 15, y, z & 15);
    }

    int getBlockMetadata(int x, int y, int z) {
        if (y < 0 || y > 255) return 0;
        final Chunk chunk = chunk(x, z);
        if (chunk.isEmpty()) return 0;
        return chunk.getBlockMetadata(x & 15, y, z & 15);
    }

    int getSavedLight(EnumSkyBlock type, int x, int y, int z) {
        if (y < 0 || y > 255) return 0;
        final Chunk chunk = chunk(x, z);
        if (chunk.isEmpty()) return 0;
        return chunk.getSavedLightValue(type, x & 15, y, z & 15);
    }

    int getHeight(int x, int z) {
        final Chunk chunk = chunk(x, z);
        if (chunk.isEmpty()) return 0;
        return chunk.getHeightValue(x & 15, z & 15);
    }

    void scanCube(int radius, int budget, CellVisitor visitor, DebugMeshData mesh) {
        final int cx = viewerX;
        final int cy = viewerY;
        final int cz = viewerZ;
        watch(cx - radius - 1, cy - radius - 1, cz - radius - 1, cx + radius + 1, cy + radius + 1, cz + radius + 1);
        this.visitor = visitor;
        this.mesh = mesh;
        this.budget = budget;
        CubeWalker.walk(cx, cy, cz, radius, this);
    }

    @Override
    public boolean cell(int x, int y, int z) {
        final Chunk chunk = chunk(x, z);
        if (chunk.isEmpty()) return true;
        column = chunk;
        columnX = x;
        columnZ = z;
        if (!visitor.visitCell(this, mesh, x, y, z)) return true;
        return --budget > 0;
    }

    void watch(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        changes.watch(slot, minX, Math.max(0, minY), minZ, maxX, Math.min(255, maxY), maxZ);
    }
}
