package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.DoubleBuffer;

public class ClipPlaneTestRenderer extends TileEntitySpecialRenderer {

    private static final DoubleBuffer KEEP_LOWER_HALF = plane(0.0, -1.0, 0.0, 0.5);
    private static final DoubleBuffer REJECT_ALL = plane(0.0, 0.0, 0.0, -1.0);

    private static DoubleBuffer plane(double a, double b, double c, double d) {
        final DoubleBuffer buf = BufferUtils.createDoubleBuffer(4);
        buf.put(a).put(b).put(c).put(d).flip();
        return buf;
    }

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glTranslated(x, y, z);
        GLSMRasterizer.glClipPlane(GL11.GL_CLIP_PLANE1, REJECT_ALL);
        GLSMEnable.glDisable(GL11.GL_CLIP_PLANE1);
        GLSMRasterizer.glClipPlane(GL11.GL_CLIP_PLANE0, KEEP_LOWER_HALF);
        GLSMEnable.glEnable(GL11.GL_CLIP_PLANE0);
        GLSMMatrices.glTranslated(0.5, 0.5, 0.5);
        bindTexture(TextureMap.locationBlocksTexture);
        RenderBlocks.getInstance().renderBlockAsItem(Blocks.gold_block, 0, 1.0f);
        GLSMEnable.glDisable(GL11.GL_CLIP_PLANE0);
        GLSMMatrices.glPopMatrix();
    }
}
