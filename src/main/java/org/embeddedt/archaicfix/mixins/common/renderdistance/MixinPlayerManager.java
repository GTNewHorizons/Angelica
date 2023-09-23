package org.embeddedt.archaicfix.mixins.common.renderdistance;

import net.minecraft.server.management.PlayerManager;
import org.embeddedt.archaicfix.ArchaicFix;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(PlayerManager.class)
public class MixinPlayerManager {
    @ModifyConstant(method = "func_152622_a", constant = @Constant(intValue = 20))
    private int increaseSendDistance(int constant) {
        return ArchaicConfig.newMaxRenderDistance;
    }
}
