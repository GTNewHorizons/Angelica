package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.mob;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.EntityLiving;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.prupe.mcpatcher.mob.LineRenderer;

@Mixin(RenderLiving.class)
public abstract class MixinRenderLiving extends RendererLivingEntity {

    public MixinRenderLiving(ModelBase modelBase, float shadowSize) {
        super(modelBase, shadowSize);
    }

    @Shadow
    protected abstract double func_110828_a(double p_110828_1_, double p_110828_3_, double p_110828_5_);

    /**
     * @author Mist475 (adapted from Paul Rupe)
     * @reason Multi-line wrap into if-statement
     */
    @SuppressWarnings("DuplicatedCode")
    @Overwrite
    protected void func_110827_b(EntityLiving entityLiving, double x, double y, double z, float n, float n2) {
        Entity entity = entityLiving.getLeashedToEntity();

        if (entity != null) {
            y -= (1.6D - (double) entityLiving.height) * 0.5D;
            Tessellator tessellator = Tessellator.instance;
            double d3 = this.func_110828_a(entity.prevRotationYaw, entity.rotationYaw, n2 * 0.5F)
                * 0.01745329238474369D;
            double d4 = this.func_110828_a(entity.prevRotationPitch, entity.rotationPitch, n2 * 0.5F)
                * 0.01745329238474369D;
            double d5 = Math.cos(d3);
            double d6 = Math.sin(d3);
            double d7 = Math.sin(d4);

            if (entity instanceof EntityHanging) {
                d5 = 0.0D;
                d6 = 0.0D;
                d7 = -1.0D;
            }

            double d8 = Math.cos(d4);
            double d9 = this.func_110828_a(entity.prevPosX, entity.posX, n2) - d5 * 0.7D - d6 * 0.5D * d8;
            double d10 = this.func_110828_a(
                entity.prevPosY + (double) entity.getEyeHeight() * 0.7D,
                entity.posY + (double) entity.getEyeHeight() * 0.7D,
                n2) - d7 * 0.5D - 0.25D;
            double d11 = this.func_110828_a(entity.prevPosZ, entity.posZ, n2) - d6 * 0.7D + d5 * 0.5D * d8;
            double d12 = this.func_110828_a(entityLiving.prevRenderYawOffset, entityLiving.renderYawOffset, n2)
                * 0.01745329238474369D + (Math.PI / 2D);
            d5 = Math.cos(d12) * (double) entityLiving.width * 0.4D;
            d6 = Math.sin(d12) * (double) entityLiving.width * 0.4D;
            double d13 = this.func_110828_a(entityLiving.prevPosX, entityLiving.posX, n2) + d5;
            double d14 = this.func_110828_a(entityLiving.prevPosY, entityLiving.posY, n2);
            double d15 = this.func_110828_a(entityLiving.prevPosZ, entityLiving.posZ, n2) + d6;
            x += d5;
            z += d6;
            double d16 = (float) (d9 - d13);
            double d17 = (float) (d10 - d14);
            double d18 = (float) (d11 - d15);
            // patch start (only change is if-wrapper)
            if (!LineRenderer.renderLine(1, x, y, z, d16, d17, d18)) {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_CULL_FACE);
                tessellator.startDrawing(5);
                int i;
                float f2;

                for (i = 0; i <= 24; ++i) {
                    if (i % 2 == 0) {
                        tessellator.setColorRGBA_F(0.5F, 0.4F, 0.3F, 1.0F);
                    } else {
                        tessellator.setColorRGBA_F(0.35F, 0.28F, 0.21000001F, 1.0F);
                    }

                    f2 = (float) i / 24.0F;
                    tessellator.addVertex(
                        x + d16 * (double) f2 + 0.0D,
                        y + d17 * (double) (f2 * f2 + f2) * 0.5D + (double) ((24.0F - (float) i) / 18.0F + 0.125F),
                        z + d18 * (double) f2);
                    tessellator.addVertex(
                        x + d16 * (double) f2 + 0.025D,
                        y + d17 * (double) (f2 * f2 + f2) * 0.5D
                            + (double) ((24.0F - (float) i) / 18.0F + 0.125F)
                            + 0.025D,
                        z + d18 * (double) f2);
                }

                tessellator.draw();
                tessellator.startDrawing(5);

                for (i = 0; i <= 24; ++i) {
                    if (i % 2 == 0) {
                        tessellator.setColorRGBA_F(0.5F, 0.4F, 0.3F, 1.0F);
                    } else {
                        tessellator.setColorRGBA_F(0.35F, 0.28F, 0.21000001F, 1.0F);
                    }

                    f2 = (float) i / 24.0F;
                    tessellator.addVertex(
                        x + d16 * (double) f2 + 0.0D,
                        y + d17 * (double) (f2 * f2 + f2) * 0.5D
                            + (double) ((24.0F - (float) i) / 18.0F + 0.125F)
                            + 0.025D,
                        z + d18 * (double) f2);
                    tessellator.addVertex(
                        x + d16 * (double) f2 + 0.025D,
                        y + d17 * (double) (f2 * f2 + f2) * 0.5D + (double) ((24.0F - (float) i) / 18.0F + 0.125F),
                        z + d18 * (double) f2 + 0.025D);
                }

                tessellator.draw();
                GL11.glEnable(GL11.GL_LIGHTING);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            }
            // patch end
            GL11.glEnable(GL11.GL_CULL_FACE);
        }
    }
}
