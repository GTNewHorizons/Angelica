package com.gtnewhorizons.angelica.mixins.early.notfine.particles;

import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.util.ReportedException;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(value = EffectRenderer.class)
public abstract class MixinEffectRenderer {

    /**
     * @author jss2a98aj
     * @reason Makes most particles render with the expected depth.
     */
    @Overwrite
    public void renderParticles(Entity entity, float p_78874_2_) {
        float f1 = ActiveRenderInfo.rotationX;
        float f2 = ActiveRenderInfo.rotationZ;
        float f3 = ActiveRenderInfo.rotationYZ;
        float f4 = ActiveRenderInfo.rotationXY;
        float f5 = ActiveRenderInfo.rotationXZ;
        EntityFX.interpPosX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * (double)p_78874_2_;
        EntityFX.interpPosY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * (double)p_78874_2_;
        EntityFX.interpPosZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * (double)p_78874_2_;

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.003921569F);
        Tessellator tessellator = Tessellator.instance;

        for (int k = 0; k < 3; ++k) {
            final int i = k;

            if (!fxLayers[i].isEmpty()) {
                switch(i) {
                    case 0:
                    default:
                        renderer.bindTexture(particleTextures);
                        break;
                    case 1:
                        renderer.bindTexture(TextureMap.locationBlocksTexture);
                        break;
                    case 2:
                        renderer.bindTexture(TextureMap.locationItemsTexture);
                }

                tessellator.startDrawingQuads();

                for (int j = 0; j < fxLayers[i].size(); ++j) {
                    final EntityFX entityfx = (EntityFX)fxLayers[i].get(j);
                    if (entityfx == null) continue;
                    tessellator.setBrightness(entityfx.getBrightnessForRender(p_78874_2_));

                    try {
                        entityfx.renderParticle(tessellator, p_78874_2_, f1, f5, f2, f3, f4);
                    } catch (Throwable throwable) {
                        CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Rendering Particle");
                        CrashReportCategory crashreportcategory = crashreport.makeCategory("Particle being rendered");
                        crashreportcategory.addCrashSectionCallable("Particle", entityfx::toString);
                        crashreportcategory.addCrashSectionCallable(
                            "Particle Type",
                            () -> i == 0 ? "MISC_TEXTURE" : (i == 1 ? "TERRAIN_TEXTURE" : (i == 2 ? "ITEM_TEXTURE" : (i == 3 ? "ENTITY_PARTICLE_TEXTURE" : "Unknown - " + i)))
                        );
                        throw new ReportedException(crashreport);
                    }
                }
                tessellator.draw();
            }
        }
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
    }

    @Shadow @Final
    private static ResourceLocation particleTextures;
    @Shadow private List[] fxLayers;
    @Shadow private TextureManager renderer;

}
