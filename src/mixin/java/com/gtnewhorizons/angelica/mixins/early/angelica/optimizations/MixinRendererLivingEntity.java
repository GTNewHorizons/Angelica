package com.gtnewhorizons.angelica.mixins.early.angelica.optimizations;

import com.gtnewhorizons.angelica.glsm.managers.GLMatrixManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity {

    @Shadow protected abstract float getDeathMaxRotation(EntityLivingBase entity);

    /**
     * @author mitchej123
     * @reason The vanilla method triggers way too many allocations for no good reason
     */
    @Overwrite
    protected void rotateCorpse(EntityLivingBase entity, float p_77043_2_, float p_77043_3_, float partialTicks) {
        GLMatrixManager.glRotatef(180.0F - p_77043_3_, 0.0F, 1.0F, 0.0F);

        if (entity.deathTime > 0) {
            float f3 = ((float)entity.deathTime + partialTicks - 1.0F) / 20.0F * 1.6F;
            f3 = MathHelper.sqrt_float(f3);

            if (f3 > 1.0F) {
                f3 = 1.0F;
            }

            GLMatrixManager.glRotatef(f3 * this.getDeathMaxRotation(entity), 0.0F, 0.0F, 1.0F);
        }
        else if ((entity instanceof EntityLiving entityLiving) && entityLiving.hasCustomNameTag() || (entity instanceof EntityPlayer entityPlayer) && !entityPlayer.getHideCape()) {

            final String s = EnumChatFormatting.getTextWithoutFormattingCodes(entity.getCommandSenderName());

            if ((s.equals("Dinnerbone") || s.equals("Grumm"))) {
                GLMatrixManager.glTranslatef(0.0F, entity.height + 0.1F, 0.0F);
                GLMatrixManager.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);
            }
        }
    }

}
