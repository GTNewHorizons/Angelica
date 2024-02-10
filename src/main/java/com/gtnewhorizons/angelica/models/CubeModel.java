package com.gtnewhorizons.angelica.models;

import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.utils.ObjectPooler;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import net.minecraft.block.Block;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import static org.lwjgl.opengl.GL11.GL_QUADS;

public class CubeModel implements QuadProvider {

    /*
    * GL only cares if the coords are clockwise or counterclockwise - not the specific order [1].
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
    *
    * [1] So apparently I was a bit wrong. GL doesn't care, but Sodium does because that's how it applies AO.
    * Change it if you dare!
    */

    // I'm sure the following constants could be condensed to a single magic array, but for clarity's sake they remain.

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

    private static final int[][] uv = {
        { 0, 0 },
        { 0, 1 },
        { 1, 1 },
        { 1, 0 }
    };

    // Add the UV.
    private static final int[][][] shiftsByDirection = {

        // DOWN 1 0 3 2
        {
            vertices[1],
            vertices[0],
            vertices[3],
            vertices[2]
        },

        //UP 4 5 6 7
        {
            vertices[4],
            vertices[5],
            vertices[6],
            vertices[7]
        },

        //NORTH 7 3 0 4
        {
            vertices[7],
            vertices[3],
            vertices[0],
            vertices[4]
        },

        //SOUTH 5 1 2 6
        {
            vertices[5],
            vertices[1],
            vertices[2],
            vertices[6]
        },

        //WEST 4 0 1 5
        {
            vertices[4],
            vertices[0],
            vertices[1],
            vertices[5]
        },

        //EAST 6 2 3 7
        {
            vertices[6],
            vertices[2],
            vertices[3],
            vertices[7]
        }
    };

    private static final int[][] initBufs = new int[6][32];

    static {
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {

            final int dirI = dir.ordinal();
            int[][] shifts = shiftsByDirection[dirI];

            for (int vi = 0; vi < 4; ++vi) {
                final int i = vi * 8;

                // XYZ
                initBufs[dirI][i + 0] = Float.floatToIntBits(shifts[vi][0]);
                initBufs[dirI][i + 1] = Float.floatToIntBits(shifts[vi][1]);
                initBufs[dirI][i + 2] = Float.floatToIntBits(shifts[vi][2]);

                // UV
                initBufs[dirI][i + 3] = Float.floatToIntBits(0);
                initBufs[dirI][i + 4] = Float.floatToIntBits(0);

                // Color, normal, brightness
                initBufs[dirI][i + 5] = 0xFFFFFFFF;
                initBufs[dirI][i + 6] = 0;
                initBufs[dirI][i + 7] = 15_728_880; // 15 sky 15 block
            }
        }
    }


    public static final ThreadLocal<CubeModel> INSTANCE = ThreadLocal.withInitial(() -> new CubeModel(false));
    private final boolean[] colorized = new boolean[6];
    private final BlockRenderer.Flags flags = new BlockRenderer.Flags(true, true, false, false);
    private static final List<Quad> EMPTY = ObjectImmutableList.of();
    private final List<Quad> ONE = new ObjectArrayList<>(1);

    public CubeModel(boolean colorized) {

        Arrays.fill(this.colorized, colorized);
        this.ONE.add(null);
    }

    public CubeModel(boolean[] colorized) {

        System.arraycopy(colorized, 0, this.colorized, 0, 6);
        this.ONE.add(null);
    }

    @Override
    public List<Quad> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, ObjectPooler<Quad> quadPool) {

        if (dir == ForgeDirection.UNKNOWN)
            return EMPTY;

        final Quad face = quadPool.getInstance();
        final int[] buf = Arrays.copyOf(initBufs[dir.ordinal()], 32); // 8 ints per vertex, four vertices per quad. A cube only has one quad per side
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

        final IIcon tex = block.getIcon(dir.ordinal(), meta);
        final int color;
        if (this.colorized[dir.ordinal()]) {
            final int c = block.colorMultiplier(world, pos.x, pos.y, pos.z);
            final int r = (c >> 16) & 0xFF;
            final int g = (c >> 8) & 0xFF;
            final int b = c & 0xFF;
            color = 0xFF << 24 | b << 16 | g << 8 | r;
        } else { color = 0xFFFFFFFF; }
        this.flags.hasColor = this.colorized[dir.ordinal()];

        for (int vi = 0; vi < 4; ++vi) {
            final int i = vi * 8;

            // UV
            buf[i + 3] = Float.floatToIntBits(tex.getInterpolatedU(uv[vi][0] * 16));
            buf[i + 4] = Float.floatToIntBits(tex.getInterpolatedV(uv[vi][1] * 16));

            // Color
            buf[i + 5] = color;
        }

        face.setState(buf, 0, this.flags, GL_QUADS, 0, 0, 0);

        // CubeModel is solid all over, and thus doesn't need to do this
        // However, you need to drop deleted quads in proper model code
        /*if (face.deleted) {

            quadPool.releaseInstance(face);
            return EMPTY;
        }*/

        ONE.set(0, face);
        return ONE;
    }
}
