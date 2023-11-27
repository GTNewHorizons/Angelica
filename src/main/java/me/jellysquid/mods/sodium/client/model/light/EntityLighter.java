package me.jellysquid.mods.sodium.client.model.light;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.entity.Entity;
import net.minecraft.world.EnumSkyBlock;

import static org.joml.Math.lerp;

public class EntityLighter {
    private static final double MIN_BOX_SIZE = 0.001D;

    private static final double MAX_LIGHT_VAL = 15.0;
    private static final double MAX_LIGHTMAP_COORD = 240.0D;

    public static int getBlendedLight(Entity entity, float tickDelta) {
        boolean calcBlockLight = !entity.isBurning();

        // Find the interpolated position of the entity
        double x1 = lerp(entity.prevPosX, entity.posX, tickDelta);
        double y1 = lerp(entity.prevPosY, entity.posY, tickDelta);
        double z1 = lerp(entity.prevPosZ, entity.posZ, tickDelta);

        // Bounding boxes with no volume cause issues, ensure they're non-zero
        // Notably, armor stands in "Marker" mode decide this is a cute thing to do
        // https://github.com/jellysquid3/sodium-fabric/issues/60
        double width = Math.max(entity.width, MIN_BOX_SIZE);
        double height = Math.max(entity.height, MIN_BOX_SIZE);

        double x2 = x1 + width;
        double y2 = y1 + height;
        double z2 = z1 + width;

        // The sampling volume of blocks which could possibly contribute light to this entity
        int bMinX = MathHelper.floor_double(x1);
        int bMinY = MathHelper.floor_double(y1);
        int bMinZ = MathHelper.floor_double(z1);
        int bMaxX = MathHelper.ceiling_double_int(x2);
        int bMaxY = MathHelper.ceiling_double_int(y2);
        int bMaxZ = MathHelper.ceiling_double_int(z2);

        // The maximum amount of light that could be contributed
        double max = 0.0D;

        // The sampled light values contributed by all sources
        double sl = 0;
        double bl = 0;

        BlockPos.Mutable pos = new BlockPos.Mutable();

        // Iterate over every block in the sampling volume
        for (int bX = bMinX; bX < bMaxX; bX++) {
            double ix1 = Math.max(bX, x1);
            double ix2 = Math.min(bX + 1, x2);

            for (int bY = bMinY; bY < bMaxY; bY++) {
                double iy1 = Math.max(bY, y1);
                double iy2 = Math.min(bY + 1, y2);

                for (int bZ = bMinZ; bZ < bMaxZ; bZ++) {
                    pos.set(bX, bY, bZ);

                    // Do not consider light-blocking volumes
                    if (entity.worldObj.getBlock(pos.x, pos.y, pos.z).isOpaqueCube()
                        && entity.worldObj.getBlockLightValue(pos.x, pos.y, pos.z) <= 0) {
                        continue;
                    }

                    // Find the intersecting volume between the entity box and the block's bounding box
                    double iz1 = Math.max(bZ, z1);
                    double iz2 = Math.min(bZ + 1, z2);

                    // The amount of light this block can contribute is the volume of the intersecting box
                    double weight = (ix2 - ix1) * (iy2 - iy1) * (iz2 - iz1);

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
        int bli = MathHelper.floor_double((bl / max) * MAX_LIGHTMAP_COORD);
        int sli = MathHelper.floor_double((sl / max) * MAX_LIGHTMAP_COORD);

        return ((sli & 0xFFFF) << 16) | (bli & 0xFFFF);
    }
}
