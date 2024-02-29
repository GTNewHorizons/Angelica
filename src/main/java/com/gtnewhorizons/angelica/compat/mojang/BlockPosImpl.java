package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizons.angelica.api.BlockPos;
import com.gtnewhorizons.angelica.api.MutableBlockPos;
import net.minecraft.world.ChunkPosition;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3i;

import static com.gtnewhorizons.angelica.api.BlockPos.unpackLongX;
import static com.gtnewhorizons.angelica.api.BlockPos.unpackLongY;
import static com.gtnewhorizons.angelica.api.BlockPos.unpackLongZ;

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
        return BlockPos.asLong(this.x, this.y, this.z);
    }

    @Override
    public BlockPosImpl set(int x, int y, int z) {
        super.set(x, y, z);
        return this;
    }

    @Override
    public BlockPosImpl set(long packedPos) {
        return set(unpackLongX(packedPos), unpackLongY(packedPos), unpackLongZ(packedPos));
    }
}
