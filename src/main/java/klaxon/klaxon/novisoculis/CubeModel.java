package klaxon.klaxon.novisoculis;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import net.minecraft.block.Block;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;
import org.apache.commons.lang3.ArrayUtils;

import static org.lwjgl.opengl.GL11.GL_QUADS;

public class CubeModel implements BakedModel {

    /*
    * GL only cares if the coords are clockwise or counterclockwise - not the specific order.
    * Which mean I get to decide it! Behold, Omni's Standard Cube Vertex Order (TM)!
    *
    *                             SOUTH (+z)
    *                                    (x + 1, y + 1, z + 1)
    *                          5 - - - - - - - - - 6
    *                          | \                 | \
    *                          |   \               |   \
    *                          |     \             |     \
    *                          |       4 - - - - - - - - - 7
    *                          |       |           |       |
    *                          |       |           |       |
    *                          |       |           |       |
    *                          1 - - - | - - - - - 2       |
    *                            \     |             \     |
    *             <- WEST (-x)     \   |               \   |     EAST (+x) ->
    *                                \ |                 \ |
    *                                  0 - - - - - - - - - 3
    *                              (x, y, z)
    *                                            NORTH (-z)
    *
    * The horizontal directions are labeled, up and down should be obvious (and would be annoying to add).
    * Vertices are written in counterclockwise order, as viewed from outside the cube, to orient the quad properly.
    * (x, y, z) is the world pos of the block. 0 and 6 have been labeled, you can figure out the rest.
    */

    // The (x, y, z) positions of the vertices
    private static final int[][] vertices = {
        { 0, 0, 0 },
        { 0, 0, 1 },
        { 1, 0, 1 },
        { 1, 0, 0 },
        { 0, 1, 0 },
        { 0, 1, 1 },
        { 1, 1, 1 },
        { 1, 1, 0 }
    };

    // Add the UV. This could probably be inlined a bit
    private static final int[][][] shiftsByDirection = {

        // DOWN 0 3 2 1
        {
            ArrayUtils.addAll(vertices[0], 0, 0),
            ArrayUtils.addAll(vertices[3], 1, 0),
            ArrayUtils.addAll(vertices[2], 1, 1),
            ArrayUtils.addAll(vertices[1], 0, 1)
        },

        //UP 4 5 6 7
        {
            ArrayUtils.addAll(vertices[4], 1, 1),
            ArrayUtils.addAll(vertices[5], 1, 0),
            ArrayUtils.addAll(vertices[6], 0, 0),
            ArrayUtils.addAll(vertices[7], 0, 1)
        },

        //NORTH 0 4 7 3
        {
            ArrayUtils.addAll(vertices[0], 1, 1),  // 0, 0
            ArrayUtils.addAll(vertices[4], 1, 0),  // 0, 1
            ArrayUtils.addAll(vertices[7], 0, 0),  // 1, 1
            ArrayUtils.addAll(vertices[3], 0, 1)   // 1, 0
        },

        //SOUTH 1 2 6 5
        {
            ArrayUtils.addAll(vertices[1], 0, 1),
            ArrayUtils.addAll(vertices[2], 1, 1),
            ArrayUtils.addAll(vertices[6], 1, 0),
            ArrayUtils.addAll(vertices[5], 0, 0)
        },

        //WEST 0 1 5 4
        {
            ArrayUtils.addAll(vertices[0], 0, 1),
            ArrayUtils.addAll(vertices[1], 1, 1),
            ArrayUtils.addAll(vertices[5], 1, 0),
            ArrayUtils.addAll(vertices[4], 0, 0)
        },

        //EAST 2 3 7 6
        {
            ArrayUtils.addAll(vertices[2], 0, 1),
            ArrayUtils.addAll(vertices[3], 1, 1),
            ArrayUtils.addAll(vertices[7], 1, 0),
            ArrayUtils.addAll(vertices[6], 0, 0)
        }
    };

    private static final BlockRenderer.Flags flags = new BlockRenderer.Flags(true, false, false, false);

    @Override
    public List<Quad> getQuads(Block block, int meta, ForgeDirection dir, Random random) {

        Quad face = new Quad();
        int[] buf = new int[32]; // 8 ints per vertex, four vertices per quad. A cube only has one quad per side
        // Format:
        // x
        // y
        // z
        // u
        // v
        // color
        // normals
        // brightness
        // repeat
        // all are float bits as ints

        final int[][] shifts = shiftsByDirection[dir.ordinal()];
        final IIcon tex = block.getIcon(dir.ordinal(), meta);

        for (int vi = 0; vi < 4; ++vi) {
            final int i = vi * 8;

            // XYZ
            buf[i + 0] = Float.floatToIntBits(shifts[vi][0]);
            buf[i + 1] = Float.floatToIntBits(shifts[vi][1]);
            buf[i + 2] = Float.floatToIntBits(shifts[vi][2]);

            // UV
            buf[i + 3] = Float.floatToIntBits(tex.getInterpolatedU(shifts[vi][3] * 16));
            buf[i + 4] = Float.floatToIntBits(tex.getInterpolatedV(shifts[vi][4] * 16));

            // Color, normal, brightness
            buf[i + 5] = 0xFFFFFFFF;
            buf[i + 6] = 0;
            buf[i + 7] = 15_728_880; // 15 sky 15 block
        }

        face.setState(buf, 0, flags, GL_QUADS, 0, 0, 0);

        return Arrays.asList(face);
    }
}
