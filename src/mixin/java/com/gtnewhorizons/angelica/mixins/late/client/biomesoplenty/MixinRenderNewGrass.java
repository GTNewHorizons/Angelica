package com.gtnewhorizons.angelica.mixins.late.client.biomesoplenty;

import biomesoplenty.client.render.blocks.RenderNewGrass;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderNewGrass.class, remap = false)
public class MixinRenderNewGrass {

    @Redirect(
        method = "renderWorldBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;func_147784_q(Lnet/minecraft/block/Block;III)Z",
            ordinal = 1),
        remap = false)
    private boolean angelica$renderBopGrassPass(RenderBlocks renderer, Block block, int x, int y, int z) {
        final int multiplier = block.colorMultiplier(renderer.blockAccess, x, y, z);
        float red = (float) (multiplier >> 16 & 255) / 255.0F;
        float green = (float) (multiplier >> 8 & 255) / 255.0F;
        float blue = (float) (multiplier & 255) / 255.0F;

        if (EntityRenderer.anaglyphEnable) {
            final float anaglyphRed = (red * 30.0F + green * 59.0F + blue * 11.0F) / 100.0F;
            final float anaglyphGreen = (red * 30.0F + green * 70.0F) / 100.0F;
            final float anaglyphBlue = (red * 30.0F + blue * 70.0F) / 100.0F;
            red = anaglyphRed;
            green = anaglyphGreen;
            blue = anaglyphBlue;
        }

        return angelica$renderGrassTopBlock(renderer, block, x, y, z, red, green, blue);
    }

    @Unique
    private static boolean angelica$renderGrassTopBlock(RenderBlocks renderer, Block block, int x, int y, int z,
        float red, float green, float blue) {
        final Tessellator tessellator = Tessellator.instance;
        final int brightness = block.getMixedBrightnessForBlock(renderer.blockAccess, x, y, z);
        boolean rendered = false;

        if (renderer.renderAllFaces || block.shouldSideBeRendered(renderer.blockAccess, x, y - 1, z, 0)) {
            tessellator.setBrightness(renderer.renderMinY > 0.0D
                ? brightness
                : block.getMixedBrightnessForBlock(renderer.blockAccess, x, y - 1, z));
            tessellator.setColorOpaque_F(0.5F, 0.5F, 0.5F);
            renderer.renderFaceYNeg(block, x, y, z, renderer.getBlockIcon(block, renderer.blockAccess, x, y, z, 0));
            rendered = true;
        }

        if (renderer.renderAllFaces || block.shouldSideBeRendered(renderer.blockAccess, x, y + 1, z, 1)) {
            tessellator.setBrightness(renderer.renderMaxY < 1.0D
                ? brightness
                : block.getMixedBrightnessForBlock(renderer.blockAccess, x, y + 1, z));
            tessellator.setColorOpaque_F(red, green, blue);
            renderer.renderFaceYPos(block, x, y, z, renderer.getBlockIcon(block, renderer.blockAccess, x, y, z, 1));
            rendered = true;
        }

        if (renderer.renderAllFaces || block.shouldSideBeRendered(renderer.blockAccess, x, y, z - 1, 2)) {
            tessellator.setBrightness(renderer.renderMinZ > 0.0D
                ? brightness
                : block.getMixedBrightnessForBlock(renderer.blockAccess, x, y, z - 1));
            tessellator.setColorOpaque_F(0.8F, 0.8F, 0.8F);
            renderer.renderFaceZNeg(block, x, y, z, renderer.getBlockIcon(block, renderer.blockAccess, x, y, z, 2));
            rendered = true;
        }

        if (renderer.renderAllFaces || block.shouldSideBeRendered(renderer.blockAccess, x, y, z + 1, 3)) {
            tessellator.setBrightness(renderer.renderMaxZ < 1.0D
                ? brightness
                : block.getMixedBrightnessForBlock(renderer.blockAccess, x, y, z + 1));
            tessellator.setColorOpaque_F(0.8F, 0.8F, 0.8F);
            renderer.renderFaceZPos(block, x, y, z, renderer.getBlockIcon(block, renderer.blockAccess, x, y, z, 3));
            rendered = true;
        }

        if (renderer.renderAllFaces || block.shouldSideBeRendered(renderer.blockAccess, x - 1, y, z, 4)) {
            tessellator.setBrightness(renderer.renderMinX > 0.0D
                ? brightness
                : block.getMixedBrightnessForBlock(renderer.blockAccess, x - 1, y, z));
            tessellator.setColorOpaque_F(0.6F, 0.6F, 0.6F);
            renderer.renderFaceXNeg(block, x, y, z, renderer.getBlockIcon(block, renderer.blockAccess, x, y, z, 4));
            rendered = true;
        }

        if (renderer.renderAllFaces || block.shouldSideBeRendered(renderer.blockAccess, x + 1, y, z, 5)) {
            tessellator.setBrightness(renderer.renderMaxX < 1.0D
                ? brightness
                : block.getMixedBrightnessForBlock(renderer.blockAccess, x + 1, y, z));
            tessellator.setColorOpaque_F(0.6F, 0.6F, 0.6F);
            renderer.renderFaceXPos(block, x, y, z, renderer.getBlockIcon(block, renderer.blockAccess, x, y, z, 5));
            rendered = true;
        }

        return rendered;
    }
}
