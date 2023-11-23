package me.jellysquid.mods.sodium.client.render.pipeline;

import com.gtnewhorizons.angelica.compat.forge.ForgeHooksClientExt;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockRenderView;
import com.gtnewhorizons.angelica.compat.mojang.BlockState;
import com.gtnewhorizons.angelica.compat.mojang.FluidState;
import com.gtnewhorizons.angelica.compat.mojang.SideShapeType;
import com.gtnewhorizons.angelica.compat.mojang.VoxelShape;
import com.gtnewhorizons.angelica.compat.mojang.VoxelShapes;
import me.jellysquid.mods.sodium.client.model.light.LightMode;
import me.jellysquid.mods.sodium.client.model.light.LightPipeline;
import me.jellysquid.mods.sodium.client.model.light.LightPipelineProvider;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuad;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadViewMutable;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFlags;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.util.Norm3b;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import org.joml.Vector3d;

import java.util.Objects;

import static org.joml.Math.lerp;

public class FluidRenderer {

	private static final float EPSILON = 0.001f;

    private final BlockPos.Mutable scratchPos = new BlockPos.Mutable();

    private final ModelQuadViewMutable quad = new ModelQuad();

    private final LightPipelineProvider lighters;

    private final QuadLightData quadLightData = new QuadLightData();
    private final int[] quadColors = new int[4];

    public FluidRenderer(Minecraft client, LightPipelineProvider lighters) {
        int normal = Norm3b.pack(0.0f, 1.0f, 0.0f);

        for (int i = 0; i < 4; i++) {
            this.quad.setNormal(i, normal);
        }

        this.lighters = lighters;
    }

    private boolean isFluidOccluded(BlockRenderView world, int x, int y, int z, ForgeDirection dir, Fluid fluid) {
        BlockPos pos = this.scratchPos.set(x, y, z);
        BlockState blockState = world.getBlockState(pos);
        BlockPos adjPos = this.scratchPos.set(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ);

        if (blockState.isOpaque()) {
            return Objects.equals(world.getFluidState(adjPos).getFluid(), fluid) || blockState.isSideSolid(world,pos,dir, SideShapeType.FULL);
            // fluidlogged or next to water, occlude sides that are solid or the same liquid
            }
        return Objects.equals(world.getFluidState(adjPos).getFluid(), fluid);
    }

    private boolean isSideExposed(BlockRenderView world, int x, int y, int z, ForgeDirection dir, float height) {
        BlockPos pos = this.scratchPos.set(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ);
        BlockState blockState = world.getBlockState(pos);

        if (blockState.isOpaque()) {
            VoxelShape shape = blockState.getCullingShape(world, pos);

            // Hoist these checks to avoid allocating the shape below
            if (shape == VoxelShapes.fullCube()) {
                // The top face always be inset, so if the shape above is a full cube it can't possibly occlude
                return dir == ForgeDirection.UP;
            } else if (shape.isEmpty()) {
                return true;
            }

            VoxelShape threshold = VoxelShapes.cuboid(0.0D, 0.0D, 0.0D, 1.0D, height, 1.0D);

            return !VoxelShapes.isSideCovered(threshold, shape, dir);
        }

        return true;
    }

    public boolean render(BlockRenderView world, FluidState fluidState, BlockPos pos, ChunkModelBuffers buffers) {
        int posX = pos.x;
        int posY = pos.y;
        int posZ = pos.z;

        Fluid fluid = fluidState.getFluid();

        boolean sfUp = this.isFluidOccluded(world, posX, posY, posZ, ForgeDirection.UP, fluid);
        boolean sfDown = this.isFluidOccluded(world, posX, posY, posZ, ForgeDirection.DOWN, fluid) ||
                !this.isSideExposed(world, posX, posY, posZ, ForgeDirection.DOWN, 0.8888889F);
        boolean sfNorth = this.isFluidOccluded(world, posX, posY, posZ, ForgeDirection.NORTH, fluid);
        boolean sfSouth = this.isFluidOccluded(world, posX, posY, posZ, ForgeDirection.SOUTH, fluid);
        boolean sfWest = this.isFluidOccluded(world, posX, posY, posZ, ForgeDirection.WEST, fluid);
        boolean sfEast = this.isFluidOccluded(world, posX, posY, posZ, ForgeDirection.EAST, fluid);

        if (sfUp && sfDown && sfEast && sfWest && sfNorth && sfSouth) {
            return false;
        }

        TextureAtlasSprite[] sprites = ForgeHooksClientExt.getFluidSprites(world, pos, fluidState);
        // TODO: Sodium - Fluids
        boolean hc = fluidState.getFluid().getColor() != 0xffffffff; //fluidState.getFluid().getAttributes().getColor() != 0xffffffff;

        boolean rendered = false;

        float h1 = this.getCornerHeight(world, posX, posY, posZ, fluidState.getFluid());
        float h2 = this.getCornerHeight(world, posX, posY, posZ + 1, fluidState.getFluid());
        float h3 = this.getCornerHeight(world, posX + 1, posY, posZ + 1, fluidState.getFluid());
        float h4 = this.getCornerHeight(world, posX + 1, posY, posZ, fluidState.getFluid());

        float yOffset = sfDown ? 0.0F : EPSILON;

        final ModelQuadViewMutable quad = this.quad;

        LightMode lightMode = hc && Minecraft.isAmbientOcclusionEnabled() ? LightMode.SMOOTH : LightMode.FLAT;
        LightPipeline lighter = this.lighters.getLighter(lightMode);

        quad.setFlags(0);

        if (!sfUp && this.isSideExposed(world, posX, posY, posZ, ForgeDirection.UP, Math.min(Math.min(h1, h2), Math.min(h3, h4)))) {
            h1 -= 0.001F;
            h2 -= 0.001F;
            h3 -= 0.001F;
            h4 -= 0.001F;

            Vector3d velocity = fluidState.getVelocity(world, pos);

            TextureAtlasSprite sprite;
            ModelQuadFacing facing;
            float u1, u2, u3, u4;
            float v1, v2, v3, v4;

            if (velocity.x == 0.0D && velocity.z == 0.0D) {
                sprite = sprites[0];
                facing = ModelQuadFacing.UP;
                u1 = sprite.getInterpolatedU(0.0D);
                v1 = sprite.getInterpolatedV(0.0D);
                u2 = u1;
                v2 = sprite.getInterpolatedV(16.0D);
                u3 = sprite.getInterpolatedU(16.0D);
                v3 = v2;
                u4 = u3;
                v4 = v1;
            } else {
                sprite = sprites[1];
                facing = ModelQuadFacing.UNASSIGNED;
                float dir = (float) Math.atan2(velocity.z, velocity.x) - (1.5707964f);
                float sin = MathHelper.sin(dir) * 0.25F;
                float cos = MathHelper.cos(dir) * 0.25F;
                u1 = sprite.getInterpolatedU(8.0F + (-cos - sin) * 16.0F);
                v1 = sprite.getInterpolatedV(8.0F + (-cos + sin) * 16.0F);
                u2 = sprite.getInterpolatedU(8.0F + (-cos + sin) * 16.0F);
                v2 = sprite.getInterpolatedV(8.0F + (cos + sin) * 16.0F);
                u3 = sprite.getInterpolatedU(8.0F + (cos + sin) * 16.0F);
                v3 = sprite.getInterpolatedV(8.0F + (cos - sin) * 16.0F);
                u4 = sprite.getInterpolatedU(8.0F + (cos - sin) * 16.0F);
                v4 = sprite.getInterpolatedV(8.0F + (-cos - sin) * 16.0F);
            }

            float uAvg = (u1 + u2 + u3 + u4) / 4.0F;
            float vAvg = (v1 + v2 + v3 + v4) / 4.0F;
            float s1 = (float) sprites[0].getIconWidth() / (sprites[0].getMaxU() - sprites[0].getMinU());
            float s2 = (float) sprites[0].getIconHeight() / (sprites[0].getMaxV() - sprites[0].getMinV());
            float s3 = 4.0F / Math.max(s2, s1);

            u1 = lerp(u1, uAvg, s3);
            u2 = lerp(u2, uAvg, s3);
            u3 = lerp(u3, uAvg, s3);
            u4 = lerp(u4, uAvg, s3);
            v1 = lerp(v1, vAvg, s3);
            v2 = lerp(v2, vAvg, s3);
            v3 = lerp(v3, vAvg, s3);
            v4 = lerp(v4, vAvg, s3);

            quad.setSprite(sprite);

            this.setVertex(quad, 0, 0.0f, h1, 0.0f, u1, v1);
            this.setVertex(quad, 1, 0.0f, h2, 1.0F, u2, v2);
            this.setVertex(quad, 2, 1.0F, h3, 1.0F, u3, v3);
            this.setVertex(quad, 3, 1.0F, h4, 0.0f, u4, v4);

            this.calculateQuadColors(quad, world, pos, lighter, ForgeDirection.UP, 1.0F, hc);
            this.flushQuad(buffers, quad, facing, false);

            if (fluidState.method_15756(world, this.scratchPos.set(posX, posY + 1, posZ))) {
                this.setVertex(quad, 3, 0.0f, h1, 0.0f, u1, v1);
                this.setVertex(quad, 2, 0.0f, h2, 1.0F, u2, v2);
                this.setVertex(quad, 1, 1.0F, h3, 1.0F, u3, v3);
                this.setVertex(quad, 0, 1.0F, h4, 0.0f, u4, v4);

                this.flushQuad(buffers, quad, ModelQuadFacing.DOWN, true);
            }

            rendered = true;
        }

        if (!sfDown) {
            TextureAtlasSprite sprite = sprites[0];

            float minU = sprite.getMinU();
            float maxU = sprite.getMaxU();
            float minV = sprite.getMinV();
            float maxV = sprite.getMaxV();
            quad.setSprite(sprite);

            this.setVertex(quad, 0, 0.0f, yOffset, 1.0F, minU, maxV);
            this.setVertex(quad, 1, 0.0f, yOffset, 0.0f, minU, minV);
            this.setVertex(quad, 2, 1.0F, yOffset, 0.0f, maxU, minV);
            this.setVertex(quad, 3, 1.0F, yOffset, 1.0F, maxU, maxV);

            this.calculateQuadColors(quad, world, pos, lighter, ForgeDirection.DOWN, 1.0F, hc);
            this.flushQuad(buffers, quad, ModelQuadFacing.DOWN, false);

            rendered = true;
        }

        quad.setFlags(ModelQuadFlags.IS_ALIGNED);

        for (ForgeDirection dir : DirectionUtil.HORIZONTAL_DIRECTIONS) {
            float c1;
            float c2;
            float x1;
            float z1;
            float x2;
            float z2;

            switch (dir) {
                case NORTH:
                    if (sfNorth) {
                        continue;
                    }

                    c1 = h1;
                    c2 = h4;
                    x1 = 0.0f;
                    x2 = 1.0F;
                    z1 = 0.001f;
                    z2 = z1;
                    break;
                case SOUTH:
                    if (sfSouth) {
                        continue;
                    }

                    c1 = h3;
                    c2 = h2;
                    x1 = 1.0F;
                    x2 = 0.0f;
                    z1 = 0.999f;
                    z2 = z1;
                    break;
                case WEST:
                    if (sfWest) {
                        continue;
                    }

                    c1 = h2;
                    c2 = h1;
                    x1 = 0.001f;
                    x2 = x1;
                    z1 = 1.0F;
                    z2 = 0.0f;
                    break;
                case EAST:
                    if (sfEast) {
                        continue;
                    }

                    c1 = h4;
                    c2 = h3;
                    x1 = 0.999f;
                    x2 = x1;
                    z1 = 0.0f;
                    z2 = 1.0F;
                    break;
                default:
                    continue;
            }

            if (this.isSideExposed(world, posX, posY, posZ, dir, Math.max(c1, c2))) {
                int adjX = posX + dir.offsetX;
                int adjY = posY + dir.offsetY;
                int adjZ = posZ + dir.offsetZ;

                TextureAtlasSprite sprite = sprites[1];
                TextureAtlasSprite oSprite = sprites[2];

                if (oSprite != null) {
                	BlockPos adjPos = this.scratchPos.set(adjX, adjY, adjZ);
                    BlockState adjBlock = world.getBlockState(adjPos);

                    if (adjBlock.shouldDisplayFluidOverlay(world, adjPos, fluidState)) {
                    	// should ignore invisible blocks, barriers, light blocks
                        // use static water when adjacent block is ice, glass, stained glass, tinted glass
                        sprite = oSprite;
                    }
                }

                float u1 = sprite.getInterpolatedU(0.0D);
                float u2 = sprite.getInterpolatedU(8.0D);
                float v1 = sprite.getInterpolatedV((1.0F - c1) * 16.0F * 0.5F);
                float v2 = sprite.getInterpolatedV((1.0F - c2) * 16.0F * 0.5F);
                float v3 = sprite.getInterpolatedV(8.0D);

                quad.setSprite(sprite);

                this.setVertex(quad, 0, x2, c2, z2, u2, v2);
                this.setVertex(quad, 1, x2, yOffset, z2, u2, v3);
                this.setVertex(quad, 2, x1, yOffset, z1, u1, v3);
                this.setVertex(quad, 3, x1, c1, z1, u1, v1);

                float br = dir.offsetZ != 0 ? 0.8F : 0.6F;

                ModelQuadFacing facing = ModelQuadFacing.fromDirection(dir);

                this.calculateQuadColors(quad, world, pos, lighter, dir, br, hc);
                this.flushQuad(buffers, quad, facing, false);

                if (sprite != oSprite) {
                    this.setVertex(quad, 0, x1, c1, z1, u1, v1);
                    this.setVertex(quad, 1, x1, yOffset, z1, u1, v3);
                    this.setVertex(quad, 2, x2, yOffset, z2, u2, v3);
                    this.setVertex(quad, 3, x2, c2, z2, u2, v2);

                    this.flushQuad(buffers, quad, facing.getOpposite(), true);
                }

                rendered = true;
            }
        }

        return rendered;
    }

    private void calculateQuadColors(ModelQuadView quad, BlockRenderView world,  BlockPos pos, LightPipeline lighter, ForgeDirection dir, float brightness, boolean colorized) {
        QuadLightData light = this.quadLightData;
        lighter.calculate(quad, pos, light, null, dir, false);

        int[] biomeColors = null;

        if (colorized) {
            // TODO: Sodium - Colorizer
        }

        for (int i = 0; i < 4; i++) {
            this.quadColors[i] = ColorABGR.mul(biomeColors != null ? biomeColors[i] : 0xFFFFFFFF, light.br[i] * brightness);
        }
    }

    private void flushQuad(ChunkModelBuffers buffers, ModelQuadView quad, ModelQuadFacing facing, boolean flip) {
        int vertexIdx, lightOrder;

        if (flip) {
            vertexIdx = 3;
            lightOrder = -1;
        } else {
            vertexIdx = 0;
            lightOrder = 1;
        }

        ModelVertexSink sink = buffers.getSink(facing);
        sink.ensureCapacity(4);

        for (int i = 0; i < 4; i++) {
            float x = quad.getX(i);
            float y = quad.getY(i);
            float z = quad.getZ(i);

            int color = this.quadColors[vertexIdx];

            float u = quad.getTexU(i);
            float v = quad.getTexV(i);

            int light = this.quadLightData.lm[vertexIdx];

            sink.writeQuad(x, y, z, color, u, v, light);

            vertexIdx += lightOrder;
        }

        TextureAtlasSprite sprite = quad.rubidium$getSprite();

        if (sprite != null) {
            buffers.getRenderData().addSprite(sprite);
        }

        sink.flush();
    }

    private void setVertex(ModelQuadViewMutable quad, int i, float x, float y, float z, float u, float v) {
        quad.setX(i, x);
        quad.setY(i, y);
        quad.setZ(i, z);
        quad.setTexU(i, u);
        quad.setTexV(i, v);
    }

    private float getCornerHeight(BlockRenderView world, int x, int y, int z, Fluid fluid) {
        int samples = 0;
        float totalHeight = 0.0F;

        for (int i = 0; i < 4; ++i) {
            int x2 = x - (i & 1);
            int z2 = z - (i >> 1 & 1);


            if (world.getFluidState(this.scratchPos.set(x2, y + 1, z2)).getFluid().equals/*matchesType*/(fluid)) {
                return 1.0F;
            }

            BlockPos pos = this.scratchPos.set(x2, y, z2);

            BlockState blockState = world.getBlockState(pos);
            FluidState fluidState = blockState.getFluidState();

            if (fluidState.getFluid().equals/*matchesType*/(fluid)) {
                float height = fluidState.getHeight(world, pos);

                if (height >= 0.8F) {
                    totalHeight += height * 10.0F;
                    samples += 10;
                } else {
                    totalHeight += height;
                    ++samples;
                }
            } else if (!blockState.getMaterial().isSolid()) {
                ++samples;
            }
        }

        return totalHeight / (float) samples;
    }
}
