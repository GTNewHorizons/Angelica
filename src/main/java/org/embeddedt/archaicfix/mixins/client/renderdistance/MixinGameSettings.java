package org.embeddedt.archaicfix.mixins.client.renderdistance;

import net.minecraft.client.settings.GameSettings;
import org.embeddedt.archaicfix.ArchaicFix;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(GameSettings.class)
public class MixinGameSettings {
    @ModifyConstant(method = "<init>(Lnet/minecraft/client/Minecraft;Ljava/io/File;)V", constant = @Constant(floatValue = 16.0f))
    private float increaseMaxDistance(float old) {
        return ArchaicConfig.newMaxRenderDistance;
    }
}
