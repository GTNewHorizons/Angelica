package com.gtnewhorizons.angelica.mixins.early.sodium;

import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(GameSettings.class)
public class MixinGameSettings {
    /**
     * @author embeddedt
     * @reason Sodium Renderer supports up to 32 chunks
     */
    @ModifyConstant(method = "<init>(Lnet/minecraft/client/Minecraft;Ljava/io/File;)V", constant = @Constant(floatValue = 16.0f))
    private float increaseMaxDistance(float old) {
        return 32.0f;
    }
}
