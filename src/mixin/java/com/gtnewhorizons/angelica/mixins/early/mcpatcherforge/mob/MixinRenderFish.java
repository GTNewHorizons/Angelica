package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.mob;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderFish;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import org.lwjglx.opengl.GL11;
import org.lwjglx.opengl.GL12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import com.prupe.mcpatcher.mob.LineRenderer;

@Mixin(RenderFish.class)
public abstract class MixinRenderFish extends Render {

    /**
     * @author Mist475 (adapted from Paul Rupe)
     * @reason multi-line code wrapped in if-statement
     */
    @SuppressWarnings({ "DuplicatedCode", "ExtractMethodRecommender" })
    @Overwrite
    public void doRender(EntityFishHook entity, double x, double y, double z, float p_76986_8_, float p_76986_9_) {
        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef((float) x, (float) y, (float) z);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GLStateManager.glScalef(0.5F, 0.5F, 0.5F);
        this.bindEntityTexture(entity);
        Tessellator tessellator = Tessellator.instance;
        byte b0 = 1;
        byte b1 = 2;
        float f2 = (float) (b0 * 8) / 128.0F;
        float f3 = (float) (b0 * 8 + 8) / 128.0F;
        float f4 = (float) (b1 * 8) / 128.0F;
        float f5 = (float) (b1 * 8 + 8) / 128.0F;
        float f6 = 1.0F;
        float f7 = 0.5F;
        float f8 = 0.5F;
        GLStateManager.glRotatef(180.0F - this.renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GLStateManager.glRotatef(-this.renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        tessellator.startDrawingQuads();
        tessellator.setNormal(0.0F, 1.0F, 0.0F);
        tessellator.addVertexWithUV(0.0F - f7, 0.0F - f8, 0.0D, f2, f5);
        tessellator.addVertexWithUV(f6 - f7, 0.0F - f8, 0.0D, f3, f5);
        tessellator.addVertexWithUV(f6 - f7, 1.0F - f8, 0.0D, f3, f4);
        tessellator.addVertexWithUV(0.0F - f7, 1.0F - f8, 0.0D, f2, f4);
        tessellator.draw();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GLStateManager.glPopMatrix();

        if (entity.field_146042_b != null) {
            float f9 = entity.field_146042_b.getSwingProgress(p_76986_9_);
            float f10 = MathHelper.sin(MathHelper.sqrt_float(f9) * (float) Math.PI);
            Vec3 vec3 = Vec3.createVectorHelper(-0.5D, 0.03D, 0.8D);
            vec3.rotateAroundX(
                -(entity.field_146042_b.prevRotationPitch
                    + (entity.field_146042_b.rotationPitch - entity.field_146042_b.prevRotationPitch) * p_76986_9_)
                    * (float) Math.PI
                    / 180.0F);
            vec3.rotateAroundY(
                -(entity.field_146042_b.prevRotationYaw
                    + (entity.field_146042_b.rotationYaw - entity.field_146042_b.prevRotationYaw) * p_76986_9_)
                    * (float) Math.PI
                    / 180.0F);
            vec3.rotateAroundY(f10 * 0.5F);
            vec3.rotateAroundX(-f10 * 0.7F);
            double d3 = entity.field_146042_b.prevPosX
                + (entity.field_146042_b.posX - entity.field_146042_b.prevPosX) * (double) p_76986_9_
                + vec3.xCoord;
            double d4 = entity.field_146042_b.prevPosY
                + (entity.field_146042_b.posY - entity.field_146042_b.prevPosY) * (double) p_76986_9_
                + vec3.yCoord;
            double d5 = entity.field_146042_b.prevPosZ
                + (entity.field_146042_b.posZ - entity.field_146042_b.prevPosZ) * (double) p_76986_9_
                + vec3.zCoord;
            double d6 = entity.field_146042_b == Minecraft.getMinecraft().thePlayer ? 0.0D
                : (double) entity.field_146042_b.getEyeHeight();

            if (this.renderManager.options.thirdPersonView > 0
                || entity.field_146042_b != Minecraft.getMinecraft().thePlayer) {
                float f11 = (entity.field_146042_b.prevRenderYawOffset
                    + (entity.field_146042_b.renderYawOffset - entity.field_146042_b.prevRenderYawOffset) * p_76986_9_)
                    * (float) Math.PI
                    / 180.0F;
                double d7 = MathHelper.sin(f11);
                double d9 = MathHelper.cos(f11);
                d3 = entity.field_146042_b.prevPosX
                    + (entity.field_146042_b.posX - entity.field_146042_b.prevPosX) * (double) p_76986_9_
                    - d9 * 0.35D
                    - d7 * 0.85D;
                d4 = entity.field_146042_b.prevPosY + d6
                    + (entity.field_146042_b.posY - entity.field_146042_b.prevPosY) * (double) p_76986_9_
                    - 0.45D;
                d5 = entity.field_146042_b.prevPosZ
                    + (entity.field_146042_b.posZ - entity.field_146042_b.prevPosZ) * (double) p_76986_9_
                    - d7 * 0.35D
                    + d9 * 0.85D;
            }

            double d14 = entity.prevPosX + (entity.posX - entity.prevPosX) * (double) p_76986_9_;
            double d8 = entity.prevPosY + (entity.posY - entity.prevPosY) * (double) p_76986_9_ + 0.25D;
            double d10 = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * (double) p_76986_9_;
            double d11 = (float) (d3 - d14);
            double d12 = (float) (d4 - d8);
            double d13 = (float) (d5 - d10);
            // patch start (= if statement)
            if (!LineRenderer.renderLine(0, x, y, z, d11, d12, d13)) {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_LIGHTING);
                tessellator.startDrawing(3);
                tessellator.setColorOpaque_I(0);
                byte b2 = 16;

                for (int i = 0; i <= b2; ++i) {
                    float f12 = (float) i / (float) b2;
                    tessellator.addVertex(
                        x + d11 * (double) f12,
                        y + d12 * (double) (f12 * f12 + f12) * 0.5D + 0.25D,
                        z + d13 * (double) f12);
                }

                tessellator.draw();
                GL11.glEnable(GL11.GL_LIGHTING);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            }
        }
    }
}
