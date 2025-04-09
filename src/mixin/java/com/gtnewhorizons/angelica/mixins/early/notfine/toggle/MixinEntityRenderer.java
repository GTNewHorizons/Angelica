package com.gtnewhorizons.angelica.mixins.early.notfine.toggle;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import org.lwjglx.opengl.GL11;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
abstract public class MixinEntityRenderer {

    @Redirect(
        method = "renderWorld(FJ)V",
        at = @At(
            value = "FIELD",
             target = "Lnet/minecraft/client/settings/GameSettings;fancyGraphics:Z",
            opcode = Opcodes.GETFIELD,
            ordinal = 0
        )
    )
    private boolean toggleWaterDetail(GameSettings settings) {
        return SettingsManager.waterDetail;
    }

    /**
     * @author Caedis
     * @reason Void fog toggle
     */
    @WrapOperation(
        method = "setupFog",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/WorldProvider;getWorldHasVoidParticles()Z"
        )
    )
    private boolean notFine$toggleVoidFog(WorldProvider provider, Operation<Boolean> original){
        return ((boolean)Settings.VOID_FOG.option.getStore()) ? original.call(provider) : false;
    }

    @ModifyArg(
        method = "setupFog",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glFogf(IF)V",
            ordinal = 14,
            remap = false
        )
    )
    private float notFine$nearFogDistance(float value) {
        return farPlaneDistance * (int) Settings.FOG_NEAR_DISTANCE.option.getStore() * 0.01F - 1F;
    }

    @Inject(method = "setupFog", at = @At(value = "INVOKE", target = "Lcpw/mods/fml/common/eventhandler/EventBus;post(Lcpw/mods/fml/common/eventhandler/Event;)Z", shift = At.Shift.AFTER, remap = false))
    private void notFine$disableFog(int p_78468_1_, float p_78468_2_, CallbackInfo ci) {
        if ((Boolean) Settings.FOG_DISABLE.option.getStore() || AngelicaConfig.enableDistantHorizons) {
            // Extremely high values cause issues, but 15 mebimeters out should be practically infinite
            GL11.glFogf(org.lwjgl.opengl.GL11.GL_FOG_START, 1024 * 1024 * 15);
            GL11.glFogf(GL11.GL_FOG_END, 1024 * 1024 * 16);
        }
    }

    @Redirect(
        method = "updateRenderer()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;updateTorchFlicker()V"
        )
    )
    private void notFine$toggleTorchFlicker(EntityRenderer instance) {
        if((boolean)Settings.MODE_LIGHT_FLICKER.option.getStore()) {
            updateTorchFlicker();
        } else {
            lightmapUpdateNeeded = true;
        }
    }

    @Shadow
    abstract void updateTorchFlicker();

    @Shadow
    private boolean lightmapUpdateNeeded;

    @Shadow
    private float farPlaneDistance;

}
