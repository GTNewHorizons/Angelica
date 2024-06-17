package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.gtnewhorizons.angelica.api.MutableBlockPos;
import net.minecraft.world.ChunkPosition;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3i;


// Should we keep this?
public class BlockPosImpl extends Vector3i implements MutableBlockPos {

    public BlockPosImpl() {
        super();
    }
    public BlockPosImpl(int x, int y, int z) {
        super(x, y, z);
    }

    public BlockPosImpl(ChunkPosition chunkPosition) {
        super(chunkPosition.chunkPosX, chunkPosition.chunkPosY, chunkPosition.chunkPosZ);
    }

    @Override
    public int getX() {
        return this.x;
    }
    @Override
    public int getY() {
        return this.y;
    }
    @Override
    public int getZ() {
        return this.z;
    }

    @Override
    public BlockPosImpl offset(ForgeDirection d) {
        return new BlockPosImpl(this.x + d.offsetX, this.y + d.offsetY, this.z + d.offsetZ);
    }

    @Override
    public BlockPosImpl down() {
        return offset(ForgeDirection.DOWN);
    }

    @Override
    public BlockPosImpl up() {
        return offset(ForgeDirection.UP);
    }

    @Override
    public long asLong() {
        return CoordinatePacker.pack(this.x, this.y, this.z);
    }

    @Override
    public MutableBlockPos move(int dX, int dY, int dZ) { x += dX; y += dY; z += dZ; return this; }

    @Override
    public MutableBlockPos move(ForgeDirection d) { x += d.offsetX; y += d.offsetY; z += d.offsetZ; return this; }

    @Override
    public BlockPosImpl set(int x, int y, int z) {
        super.set(x, y, z);
        return this;
    }

    @Override
    public BlockPosImpl set(BlockPos b, ForgeDirection d) {
        super.set(b.getX() + d.offsetX, b.getY() + d.offsetY, b.getZ() + d.offsetZ);
        return this;
    }

    @Override
    public BlockPosImpl set(BlockPos b) {
        super.set(b.getX(), b.getY(), b.getZ());
        return this;
    }

    @Override
    public BlockPosImpl set(long packedPos) {
        CoordinatePacker.unpack(packedPos, this);
        return this;
    }
}
