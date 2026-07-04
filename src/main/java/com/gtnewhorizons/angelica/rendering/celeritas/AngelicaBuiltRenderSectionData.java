package com.gtnewhorizons.angelica.rendering.celeritas;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;

import java.util.Arrays;

public class AngelicaBuiltRenderSectionData extends MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity> {
    public static final float[] EMPTY_BOUNDS = new float[0];

    public float[] culledBlockEntityBounds = EMPTY_BOUNDS;

    public double maxTeRenderDistSq = Double.POSITIVE_INFINITY;

    public int cullEpoch = -1;
    public int cullVerdict;

    public static void packSectionLocalBounds(FloatArrayList out, AxisAlignedBB aabb, int originX, int originY, int originZ) {
        out.add((float) (aabb.minX - originX));
        out.add((float) (aabb.minY - originY));
        out.add((float) (aabb.minZ - originZ));
        out.add((float) (aabb.maxX - originX));
        out.add((float) (aabb.maxY - originY));
        out.add((float) (aabb.maxZ - originZ));
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        final AngelicaBuiltRenderSectionData other = (AngelicaBuiltRenderSectionData) o;
        return maxTeRenderDistSq == other.maxTeRenderDistSq && Arrays.equals(culledBlockEntityBounds, other.culledBlockEntityBounds);
    }

    @Override
    public int hashCode() {
        int result = 31 * super.hashCode() + Arrays.hashCode(culledBlockEntityBounds);
        return 31 * result + Double.hashCode(maxTeRenderDistSq);
    }
}
