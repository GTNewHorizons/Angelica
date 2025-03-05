package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cit.client.renderer.entity;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.glBlendFunc;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glColor4f;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glDepthFunc;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glDepthMask;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glDisable;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glEnable;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glLoadIdentity;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glMatrixMode;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glPopMatrix;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glPushMatrix;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glRotatef;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glScalef;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glTranslatef;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.Logger;
import org.lwjglx.opengl.GL11;
import org.lwjglx.opengl.GL12;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.prupe.mcpatcher.cit.CITUtils;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRenderEntityLiving extends Render {

    @Final
    @Shadow
    private static Logger logger;
    @Final
    @Shadow
    private static ResourceLocation RES_ITEM_GLINT;
    @Shadow
    public ModelBase mainModel;
    @Shadow
    public ModelBase renderPassModel;

    @Shadow
    protected abstract float interpolateRotation(float angle1, float angle2, float p_77034_3_);

    @Shadow
    protected abstract void renderModel(EntityLivingBase entityLivingBase, float p_77036_2_, float p_77036_3_,
        float p_77036_4_, float p_77036_5_, float p_77036_6_, float p_77036_7_);

    @Shadow
    protected abstract void renderLivingAt(EntityLivingBase entityLivingBase, double p_77039_2_, double p_77039_4_,
        double p_77039_6_);

    @Shadow
    protected abstract void rotateCorpse(EntityLivingBase entityLivingBase, float p_77043_2_, float p_77043_3_,
        float p_77043_4_);

    @Shadow
    protected abstract float renderSwingProgress(EntityLivingBase entityLivingBase, float p_77040_2_);

    @Shadow
    protected abstract float handleRotationFloat(EntityLivingBase entityLivingBase, float p_77044_2_);

    @Shadow
    protected abstract void renderEquippedItems(EntityLivingBase entityLivingBase, float p_77029_2_);

    @Shadow
    protected abstract int inheritRenderPass(EntityLivingBase entityLivingBase, int p_77035_2_, float p_77035_3_);

    @Shadow
    protected abstract int shouldRenderPass(EntityLivingBase entityLivingBase, int p_77032_2_, float p_77032_3_);

    @Shadow
    protected abstract void func_82408_c(EntityLivingBase entityLivingBase, int p_82408_2_, float p_82408_3_);

    @Shadow
    protected abstract int getColorMultiplier(EntityLivingBase entityLivingBase, float p_77030_2_, float p_77030_3_);

    @Shadow
    protected abstract void preRenderCallback(EntityLivingBase entityLivingBase, float p_77041_2_);

    @Shadow
    protected abstract void passSpecialRender(EntityLivingBase entityLivingBase, double p_77033_2_, double p_77033_4_,
        double p_77033_6_);

    /**
     * @author Mist475 (adapted from Paul Rupe)
     * @reason if statement modified into else-if
     */
    @SuppressWarnings("DuplicatedCode")
    @Overwrite
    public void doRender(EntityLivingBase entity, double x, double y, double z, float p_76986_8_, float p_76986_9_) {
        if (MinecraftForge.EVENT_BUS
            .post(new RenderLivingEvent.Pre(entity, (RendererLivingEntity) (Object) this, x, y, z))) return;
        glPushMatrix();
        glDisable(GL11.GL_CULL_FACE);
        this.mainModel.onGround = this.renderSwingProgress(entity, p_76986_9_);

        if (this.renderPassModel != null) {
            this.renderPassModel.onGround = this.mainModel.onGround;
        }

        this.mainModel.isRiding = entity.isRiding();

        if (this.renderPassModel != null) {
            this.renderPassModel.isRiding = this.mainModel.isRiding;
        }

        this.mainModel.isChild = entity.isChild();

        if (this.renderPassModel != null) {
            this.renderPassModel.isChild = this.mainModel.isChild;
        }

        try {
            float f2 = this.interpolateRotation(entity.prevRenderYawOffset, entity.renderYawOffset, p_76986_9_);
            float f3 = this.interpolateRotation(entity.prevRotationYawHead, entity.rotationYawHead, p_76986_9_);
            float f4;

            if (entity.isRiding() && entity.ridingEntity instanceof EntityLivingBase entitylivingbase1) {
                f2 = this.interpolateRotation(
                    entitylivingbase1.prevRenderYawOffset,
                    entitylivingbase1.renderYawOffset,
                    p_76986_9_);
                f4 = MathHelper.wrapAngleTo180_float(f3 - f2);

                if (f4 < -85.0F) {
                    f4 = -85.0F;
                }

                if (f4 >= 85.0F) {
                    f4 = 85.0F;
                }

                f2 = f3 - f4;

                if (f4 * f4 > 2500.0F) {
                    f2 += f4 * 0.2F;
                }
            }

            float f13 = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * p_76986_9_;
            this.renderLivingAt(entity, x, y, z);
            f4 = this.handleRotationFloat(entity, p_76986_9_);
            this.rotateCorpse(entity, f4, f2, p_76986_9_);
            float f5 = 0.0625F;
            glEnable(GL12.GL_RESCALE_NORMAL);
            glScalef(-1.0F, -1.0F, 1.0F);
            this.preRenderCallback(entity, p_76986_9_);
            glTranslatef(0.0F, -24.0F * f5 - 0.0078125F, 0.0F);
            float f6 = entity.prevLimbSwingAmount + (entity.limbSwingAmount - entity.prevLimbSwingAmount) * p_76986_9_;
            float f7 = entity.limbSwing - entity.limbSwingAmount * (1.0F - p_76986_9_);

            if (entity.isChild()) {
                f7 *= 3.0F;
            }

            if (f6 > 1.0F) {
                f6 = 1.0F;
            }

            glEnable(GL11.GL_ALPHA_TEST);
            this.mainModel.setLivingAnimations(entity, f7, f6, p_76986_9_);
            this.renderModel(entity, f7, f6, f4, f3 - f2, f13, f5);
            int j;
            float f8;
            float f9;
            float f10;

            for (int i = 0; i < 4; ++i) {
                j = this.shouldRenderPass(entity, i, p_76986_9_);

                if (j > 0) {
                    this.renderPassModel.setLivingAnimations(entity, f7, f6, p_76986_9_);
                    this.renderPassModel.render(entity, f7, f6, f4, f3 - f2, f13, f5);

                    if ((j & 240) == 16) {
                        this.func_82408_c(entity, i, p_76986_9_);
                        this.renderPassModel.render(entity, f7, f6, f4, f3 - f2, f13, f5);
                    }
                    // patch start
                    if (CITUtils.setupArmorEnchantments(entity, i)) {
                        while (CITUtils.preRenderArmorEnchantment()) {
                            this.renderPassModel.render(entity, f7, f6, f4, f3 - f2, f13, f5);
                            CITUtils.postRenderArmorEnchantment();
                        }
                    } else if ((j & 15) == 15) {
                        // if -> else if
                        // patch end
                        f8 = (float) entity.ticksExisted + p_76986_9_;
                        this.bindTexture(RES_ITEM_GLINT);
                        glEnable(GL11.GL_BLEND);
                        f9 = 0.5F;
                        glColor4f(f9, f9, f9, 1.0F);
                        glDepthFunc(GL11.GL_EQUAL);
                        glDepthMask(false);

                        for (int k = 0; k < 2; ++k) {
                            glDisable(GL11.GL_LIGHTING);
                            f10 = 0.76F;
                            glColor4f(0.5F * f10, 0.25F * f10, 0.8F * f10, 1.0F);
                            glBlendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
                            glMatrixMode(GL11.GL_TEXTURE);
                            glLoadIdentity();
                            float f11 = f8 * (0.001F + (float) k * 0.003F) * 20.0F;
                            float f12 = 0.33333334F;
                            glScalef(f12, f12, f12);
                            glRotatef(30.0F - (float) k * 60.0F, 0.0F, 0.0F, 1.0F);
                            glTranslatef(0.0F, f11, 0.0F);
                            glMatrixMode(GL11.GL_MODELVIEW);
                            this.renderPassModel.render(entity, f7, f6, f4, f3 - f2, f13, f5);
                        }

                        glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                        glMatrixMode(GL11.GL_TEXTURE);
                        glDepthMask(true);
                        glLoadIdentity();
                        glMatrixMode(GL11.GL_MODELVIEW);
                        glEnable(GL11.GL_LIGHTING);
                        glDisable(GL11.GL_BLEND);
                        glDepthFunc(GL11.GL_LEQUAL);
                    }

                    glDisable(GL11.GL_BLEND);
                    glEnable(GL11.GL_ALPHA_TEST);
                }
            }

            glDepthMask(true);
            this.renderEquippedItems(entity, p_76986_9_);
            float f14 = entity.getBrightness(p_76986_9_);
            j = this.getColorMultiplier(entity, f14, p_76986_9_);
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);

            if ((j >> 24 & 255) > 0 || entity.hurtTime > 0 || entity.deathTime > 0) {
                glDisable(GL11.GL_TEXTURE_2D);
                glDisable(GL11.GL_ALPHA_TEST);
                glEnable(GL11.GL_BLEND);
                glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                glDepthFunc(GL11.GL_EQUAL);

                if (entity.hurtTime > 0 || entity.deathTime > 0) {
                    glColor4f(f14, 0.0F, 0.0F, 0.4F);
                    this.mainModel.render(entity, f7, f6, f4, f3 - f2, f13, f5);

                    for (int l = 0; l < 4; ++l) {
                        if (this.inheritRenderPass(entity, l, p_76986_9_) >= 0) {
                            glColor4f(f14, 0.0F, 0.0F, 0.4F);
                            this.renderPassModel.render(entity, f7, f6, f4, f3 - f2, f13, f5);
                        }
                    }
                }

                if ((j >> 24 & 255) > 0) {
                    f8 = (float) (j >> 16 & 255) / 255.0F;
                    f9 = (float) (j >> 8 & 255) / 255.0F;
                    float f15 = (float) (j & 255) / 255.0F;
                    f10 = (float) (j >> 24 & 255) / 255.0F;
                    glColor4f(f8, f9, f15, f10);
                    this.mainModel.render(entity, f7, f6, f4, f3 - f2, f13, f5);

                    for (int i1 = 0; i1 < 4; ++i1) {
                        if (this.inheritRenderPass(entity, i1, p_76986_9_) >= 0) {
                            glColor4f(f8, f9, f15, f10);
                            this.renderPassModel.render(entity, f7, f6, f4, f3 - f2, f13, f5);
                        }
                    }
                }

                glDepthFunc(GL11.GL_LEQUAL);
                glDisable(GL11.GL_BLEND);
                glEnable(GL11.GL_ALPHA_TEST);
                glEnable(GL11.GL_TEXTURE_2D);
            }

            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        } catch (Exception exception) {
            logger.error("Couldn't render entity", exception);
        }

        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        glEnable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        glEnable(GL11.GL_CULL_FACE);
        glPopMatrix();
        this.passSpecialRender(entity, x, y, z);
        MinecraftForge.EVENT_BUS
            .post(new RenderLivingEvent.Post(entity, (RendererLivingEntity) (Object) this, x, y, z));
    }
}
