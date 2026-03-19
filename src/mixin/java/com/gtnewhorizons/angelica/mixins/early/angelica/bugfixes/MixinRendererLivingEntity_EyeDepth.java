package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderDragon;
import net.minecraft.client.renderer.entity.RenderEnderman;
import net.minecraft.client.renderer.entity.RenderSpider;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(RendererLivingEntity.class)
public class MixinRendererLivingEntity_EyeDepth {

    /**
     * Push the eye out just a tad towards the camera. Fixes Z-Fighting on the eyes.
     */
    @WrapOperation(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V"),
        slice = @Slice(
            from = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;shouldRenderPass(Lnet/minecraft/entity/EntityLivingBase;IF)I")
        )
    )
    private void angelica$eyePassPolygonOffset(ModelBase instance, Entity entity, float a, float b, float c, float d, float e, float f, Operation<Void> original) {
        if (!((Object) this instanceof RenderSpider) && !((Object) this instanceof RenderEnderman) && !((Object) this instanceof RenderDragon)) {
            original.call(instance, entity, a, b, c, d, e, f);
            return;
        }
        GLStateManager.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glPolygonOffset(-1.0f, -1.0f);
        original.call(instance, entity, a, b, c, d, e, f);
        GLStateManager.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glPolygonOffset(0.0f, 0.0f);
    }
}
