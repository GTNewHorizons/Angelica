package me.jellysquid.mods.sodium.client.model.light;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.entity.Entity;
import net.minecraft.world.EnumSkyBlock;

import static org.joml.Math.lerp;

public class EntityLighter {
    private static final double MIN_BOX_SIZE = 0.001D;

    private static final double MAX_LIGHT_VAL = 15.0;
    private static final double MAX_LIGHTMAP_COORD = 240.0D;

    public static int getBlendedLight(Entity entity, float tickDelta) {
        final boolean calcBlockLight = !entity.isBurning();

        // Find the interpolated position of the entity
        final double x1 = lerp(entity.prevPosX, entity.posX, tickDelta);
        final double y1 = lerp(entity.prevPosY, entity.posY, tickDelta);
        final double z1 = lerp(entity.prevPosZ, entity.posZ, tickDelta);

        // Bounding boxes with no volume cause issues, ensure they're non-zero
        // Notably, armor stands in "Marker" mode decide this is a cute thing to do
        // https://github.com/jellysquid3/sodium-fabric/issues/60
        final double width = Math.max(entity.width, MIN_BOX_SIZE);
        final double height = Math.max(entity.height, MIN_BOX_SIZE);

        final double x2 = x1 + width;
        final double y2 = y1 + height;
        final double z2 = z1 + width;

        // The sampling volume of blocks which could possibly contribute light to this entity
        final int bMinX = MathHelper.floor_double(x1);
        final int bMinY = MathHelper.floor_double(y1);
        final int bMinZ = MathHelper.floor_double(z1);
        final int bMaxX = MathHelper.ceiling_double_int(x2);
        final int bMaxY = MathHelper.ceiling_double_int(y2);
        final int bMaxZ = MathHelper.ceiling_double_int(z2);

        // The maximum amount of light that could be contributed
        double max = 0.0D;

        // The sampled light values contributed by all sources
        double sl = 0;
        double bl = 0;

        final BlockPos pos = new BlockPos();

        // Iterate over every block in the sampling volume
        for (int bX = bMinX; bX < bMaxX; bX++) {
            final double ix1 = Math.max(bX, x1);
            final double ix2 = Math.min(bX + 1, x2);

            for (int bY = bMinY; bY < bMaxY; bY++) {
                final double iy1 = Math.max(bY, y1);
                final double iy2 = Math.min(bY + 1, y2);

                for (int bZ = bMinZ; bZ < bMaxZ; bZ++) {
                    pos.set(bX, bY, bZ);

                    // Do not consider light-blocking volumes
                    if (entity.worldObj.getBlock(pos.x, pos.y, pos.z).isOpaqueCube()
                        && entity.worldObj.getBlockLightValue(pos.x, pos.y, pos.z) <= 0) {
                        continue;
                    }

                    // Find the intersecting volume between the entity box and the block's bounding box
                    final double iz1 = Math.max(bZ, z1);
                    final double iz2 = Math.min(bZ + 1, z2);

                    // The amount of light this block can contribute is the volume of the intersecting box
                    final double weight = (ix2 - ix1) * (iy2 - iy1) * (iz2 - iz1);

                    // Keep count of how much light could've been contributed
                    max += weight;

                    // note: lighter.bridge$getSkyLight(entity, pos) and lighter.bridge$getBlockLight(entity, pos)
                    // were replaced, mixin+this method de-generified. as far as I can tell they only existed because
                    // Sodium had a weird setup just for paintings, don't think we need it.

                    // Sum the light actually contributed by this volume
                    sl += weight * (entity.worldObj.getSkyBlockTypeBrightness(EnumSkyBlock.Sky, pos.x, pos.y, pos.z) / MAX_LIGHT_VAL);

                    if (calcBlockLight) {
                        bl += weight * (entity.worldObj.getSkyBlockTypeBrightness(EnumSkyBlock.Block, pos.x, pos.y, pos.z) / MAX_LIGHT_VAL);
                    } else {
                        bl += weight;
                    }
                }
            }
        }

        // The final light value is calculated from the percentage of light contributed out of the total maximum
        final int bli = MathHelper.floor_double((bl / max) * MAX_LIGHTMAP_COORD);
        final int sli = MathHelper.floor_double((sl / max) * MAX_LIGHTMAP_COORD);

        return ((sli & 0xFFFF) << 16) | (bli & 0xFFFF);
    }
}
