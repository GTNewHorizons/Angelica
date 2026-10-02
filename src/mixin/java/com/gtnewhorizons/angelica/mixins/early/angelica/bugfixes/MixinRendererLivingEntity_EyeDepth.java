package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.helpers.RendererLivingEntityHelper;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(RendererLivingEntity.class)
public class MixinRendererLivingEntity_EyeDepth {

    @Surround(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V"),
        slice = @Slice(
            from = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;shouldRenderPass(Lnet/minecraft/entity/EntityLivingBase;IF)I"),
            to = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;renderEquippedItems(Lnet/minecraft/entity/EntityLivingBase;F)V")
        )
    )
    private void angelica$eyePassPolygonOffset() {
        @Surround.Carry
        boolean eyes = RendererLivingEntityHelper.hasEyePass(this);
        if (eyes) {
            GLStateManager.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GLStateManager.glPolygonOffset(-1.0f, -1.0f);
        }
    }

    @Surround.Finally
    private void angelica$eyePassPolygonOffsetEnd(@Surround.Carry boolean eyes) {
        if (!eyes) return;
        GLStateManager.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glPolygonOffset(0.0f, 0.0f);
    }
}
