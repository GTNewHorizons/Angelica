package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.rendering.DeferredCreeperAura;
import net.minecraft.client.renderer.entity.RenderCreeper;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.monster.EntityCreeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderCreeper.class)
public class MixinRenderCreeper_AuraDepth {

    /**
     * Mark that a charged creeper aura pass is about to execute. The method runs normally
     * so any mixins added by mods within shouldRenderPass still fire. The flag is consumed by the
     * WrapOperation on renderPassModel.render() in doRender, which defers the actual render.
     * See MixinRendererLivingEntity_DeferredCreeperAura
     *
     * The accessor is passed as a lambda so the utility class in src/main doesn't need
     * to import the mixin accessor interface from src/mixin.
     */
    @Inject(method = "shouldRenderPass(Lnet/minecraft/entity/monster/EntityCreeper;IF)I",
        at = @At("HEAD"))
    private void angelica$markAuraPass(EntityCreeper creeper, int pass, float partialTick, CallbackInfoReturnable<Integer> cir) {
        if (pass == 1 && creeper.getPowered() && !DeferredCreeperAura.isReplaying()) {
            AccessorRenderCreeper accessor = (AccessorRenderCreeper) this;
            DeferredCreeperAura.markAuraPass(
                accessor::invokeShouldRenderPass,
                (RendererLivingEntity) (Object) this,
                creeper, partialTick
            );
        }
    }
}
