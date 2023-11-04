package me.jellysquid.mods.sodium.mixin.core;

import net.minecraft.client.render.OverlayVertexConsumer;
import net.minecraft.client.render.model.BakedQuadFactory;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Direction.class)
public class MixinDirection {
    /**
     * Used by:
     * <ul>
     *     <li>{@link OverlayVertexConsumer}</li>
     *     <li>UV-locked faces and cullfaces in {@link JsonUnbakedModel} and {@link BakedQuadFactory}</li>
     *     <li>Raycasts, when creating the {@link BlockHitResult}</li>
     * </ul>
     *
     * @author <a href="mailto:skaggsm333@gmail.com">Mitchell Skaggs</a>
     * @reason Avoid looping over all directions and computing the dot product
     */
    @Overwrite
    public static Direction getFacing(float x, float y, float z) {
        // Vanilla quirk: return NORTH if all entries are zero
        if (x == 0 && y == 0 && z == 0)
            return Direction.NORTH;

        // First choice in ties: negative, positive; Y, Z, X
        float yM = Math.abs(y);
        float zM = Math.abs(z);
        float xM = Math.abs(x);

        if (yM >= zM) {
            if (yM >= xM) {
                // Y biggest
                if (y <= 0) {
                    return Direction.DOWN;
                } else /* y > 0 */ {
                    return Direction.UP;
                }
            } else /* zM <= yM < xM */ {
                // X biggest, fall through
            }
        } else /* yM < zM */ {
            if (zM >= xM) {
                // Z biggest
                if (z <= 0) {
                    return Direction.NORTH;
                } else /* z > 0 */ {
                    return Direction.SOUTH;
                }
            } else /* yM < zM < xM */ {
                // X biggest, fall through
            }
        }

        // X biggest
        if (x <= 0) {
            return Direction.WEST;
        } else /* x > 0 */ {
            return Direction.EAST;
        }
    }
}