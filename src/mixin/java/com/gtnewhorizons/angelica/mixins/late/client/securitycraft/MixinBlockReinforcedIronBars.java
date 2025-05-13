package com.gtnewhorizons.angelica.mixins.late.client.securitycraft;

import net.geforcemods.securitycraft.blocks.reinforced.BlockReinforcedIronBars;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BlockReinforcedIronBars.class, remap = false)
public abstract class MixinBlockReinforcedIronBars {

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/common/ObfuscationReflectionHelper;setPrivateValue(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;I)V"
        )
    )
    private void angelica$fixReflection(Class<?> clazz, Object instance, Object value, int fieldIndex) {
        ((Block) instance).setStepSound(Block.soundTypeMetal);
    }
}
