package com.gtnewhorizons.angelica.mixins.early.notfine.toggle;

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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

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
        // Extremely high values cause issues, but 15 mebimeters out should be practically infinite
        if ((Boolean) Settings.FOG_DISABLE.option.getStore()) return 1024 * 1024 * 15;
        return farPlaneDistance * (int) Settings.FOG_NEAR_DISTANCE.option.getStore() * 0.01F - 1F;
    }

    @ModifyArg(
        method = "setupFog(IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glFogf(IF)V",
            ordinal = 15,
            remap = false
        )
    )
    private float notFine$replaceFarFogDistance(float value) {
        if ((Boolean) Settings.FOG_DISABLE.option.getStore()) return 1024 * 1024 * 16;
        return value;
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
