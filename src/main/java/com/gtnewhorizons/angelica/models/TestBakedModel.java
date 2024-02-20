package com.gtnewhorizons.angelica.models;

import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.models.json.JsonModel;
import com.gtnewhorizons.angelica.utils.ObjectPooler;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import net.minecraft.block.Block;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * This is a half-baked port of Fabric's sample custom model, for reference.
 */
public class TestBakedModel implements QuadProvider {

    private static final String[] SPRITE_IDS = new String[]{
        "furnace_front_on",
        "furnace_top"
    };
    private final IIcon[] sprites = new IIcon[SPRITE_IDS.length];

    // Some constants to avoid magic numbers, these need to match the SPRITE_IDS
    private static final int SPRITE_SIDE = 0;
    private static final int SPRITE_TOP = 1;

    private final Quad[] quadStore = new Quad[6];
    private static final List<Quad> EMPTY = ObjectImmutableList.of();

    //@Override
    public Collection<ResourceLocation> getModelDependencies() {
        return Arrays.asList(); // This model does not depend on other models.
    }

    public void setParents(Function<ResourceLocation, JsonModel> modelLoader) {
        // This is related to model parents, it's not required for our use case
    }

    public void bake() {

        // Get the sprites
        for(int i = 0; i < SPRITE_IDS.length; ++i) {
            //sprites[i] = getBlockIcon(SPRITE_IDS[i]);
        }

        // Build the mesh using my API
        final NdQuadBuilder builder = new NdQuadBuilder();

        // This is a cube, it only has sides on VALID_DIRECTIONS
        // But something like tall grass will need to go over UNKNOWN too
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {

            // UP and DOWN share the Y axis
            int spriteIdx = direction == ForgeDirection.UP || direction == ForgeDirection.DOWN ? SPRITE_TOP : SPRITE_SIDE;
            // Add a new face to the mesh
            builder.square(direction, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f);
            // Set the sprite of the face, must be called after .square()
            // We haven't specified any UV coordinates, so we want to use the whole texture. BAKE_LOCK_UV does exactly that.
            builder.spriteBake(sprites[spriteIdx], NdQuadBuilder.BAKE_LOCK_UV);
            // Enable texture usage
            builder.color(-1, -1, -1, -1);
            // Add the quad to the mesh
            quadStore[direction.ordinal()] = builder.build(new Quad());
        }

    }

    @Override
    public List<Quad> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, ObjectPooler<Quad> quadPool) {

        if (dir == ForgeDirection.UNKNOWN)
            return EMPTY;

        final Quad q = quadPool.getInstance();
        //q.copyFrom(this.quadStore[dir.ordinal()]);
        final List<Quad> ret = new ArrayList<>();
        ret.add(q);
        return ret;
    }
}
