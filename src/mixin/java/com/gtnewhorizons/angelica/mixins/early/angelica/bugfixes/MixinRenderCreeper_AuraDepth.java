package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.rendering.DeferredEntityOverlay;
import net.minecraft.client.renderer.entity.RenderCreeper;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.monster.EntityCreeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderCreeper.class)
public abstract class MixinRenderCreeper_AuraDepth {

    @Shadow
    protected abstract int shouldRenderPass(EntityCreeper entity, int pass, float partialTick);

    @Unique
    private DeferredEntityOverlay.ShouldRenderPassFn angelica$cachedPassFn;

    /**
     * Mark that a charged creeper aura pass is about to execute. The method runs normally
     * so any mixins added by mods within shouldRenderPass still fire. The flag is consumed by the
     * WrapOperation on renderPassModel.render() in doRender, which defers the actual render.
     * See MixinRendererLivingEntity_DeferredEntityOverlay
     */
    @Inject(method = "shouldRenderPass(Lnet/minecraft/entity/monster/EntityCreeper;IF)I",
        at = @At("HEAD"))
    private void angelica$markOverlayPass(EntityCreeper creeper, int pass, float partialTick, CallbackInfoReturnable<Integer> cir) {
        if (pass == 1 && creeper.getPowered() && !DeferredEntityOverlay.isReplaying()) {
            if (angelica$cachedPassFn == null) {
                angelica$cachedPassFn = (entity, pass2, tick) -> this.shouldRenderPass((EntityCreeper) entity, pass2, tick);
            }
            DeferredEntityOverlay.markOverlayPass(
                angelica$cachedPassFn,
                (RendererLivingEntity) (Object) this,
                creeper, partialTick
            );
        }
    }
}
