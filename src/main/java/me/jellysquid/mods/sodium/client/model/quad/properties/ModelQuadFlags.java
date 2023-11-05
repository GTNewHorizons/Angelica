package me.jellysquid.mods.sodium.client.model.quad.properties;

import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.Direction;

public class ModelQuadFlags {
    /**
     * Indicates that the quad does not fully cover the given face for the model.
     */
    public static final int IS_PARTIAL = 0b001;

    /**
     * Indicates that the quad is parallel to its light face.
     */
    public static final int IS_PARALLEL = 0b010;

    /**
     * Indicates that the quad is aligned to the block grid.
     * This flag is only set if {@link #IS_PARALLEL} is set.
     */
    public static final int IS_ALIGNED = 0b100;

    /**
     * @return True if the bit-flag of {@link ModelQuadFlags} contains the given flag
     */
    public static boolean contains(int flags, int mask) {
        return (flags & mask) != 0;
    }

    /**
     * Calculates the properties of the given quad. This data is used later by the light pipeline in order to make
     * certain optimizations.
     */
    public static int getQuadFlags(BakedQuad bakedQuad) {
        ModelQuadView quad = (ModelQuadView) bakedQuad;
        Direction face = bakedQuad.getFace();

        float minX = 32.0F;
        float minY = 32.0F;
        float minZ = 32.0F;

        float maxX = -32.0F;
        float maxY = -32.0F;
        float maxZ = -32.0F;

        for (int i = 0; i < 4; ++i) {
            float x = quad.getX(i);
            float y = quad.getY(i);
            float z = quad.getZ(i);

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        boolean partial = false;
        
        switch (face.getAxis()) {
            case X : 
            	partial = minY >= 0.0001f || minZ >= 0.0001f || maxY <= 0.9999F || maxZ <= 0.9999F;
            	break;
            case Y : 
            	partial = minX >= 0.0001f || minZ >= 0.0001f || maxX <= 0.9999F || maxZ <= 0.9999F;
            	break;
            case Z : 
            	partial = minX >= 0.0001f || minY >= 0.0001f || maxX <= 0.9999F || maxY <= 0.9999F;
            	break;
        };

        boolean parallel = false;
        
        switch(face.getAxis()) {
            case X :
            	parallel = minX == maxX;
            	break;
            case Y :
            	parallel = minY == maxY;
            	break;
            case Z :
            	parallel = minZ == maxZ;
            	break;
        };

        boolean aligned = false;
        boolean flag = false;
        
        switch (face) {
            case DOWN :
            	flag = minY < 0.0001f;
            	break;
            case UP :
            	flag = maxY > 0.9999F;
            	break;
            case NORTH :
            	flag = minZ < 0.0001f;
            	break;
            case SOUTH :
            	flag = maxZ > 0.9999F;
            	break;
            case WEST :
            	flag = minX < 0.0001f;
            	break;
            case EAST :
            	flag = maxX > 0.9999F;
            	break;
        };
        
        aligned = parallel && flag;

        int flags = 0;

        if (partial) {
            flags |= IS_PARTIAL;
        }

        if (parallel) {
            flags |= IS_PARALLEL;
        }

        if (aligned) {
            flags |= IS_ALIGNED;
        }

        return flags;
    }
}