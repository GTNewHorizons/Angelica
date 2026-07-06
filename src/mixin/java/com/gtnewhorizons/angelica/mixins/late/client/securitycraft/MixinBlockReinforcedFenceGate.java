package com.gtnewhorizons.angelica.mixins.late.client.securitycraft;

import net.geforcemods.securitycraft.blocks.reinforced.BlockReinforcedFenceGate;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.material.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BlockReinforcedFenceGate.class, remap = false)
public class MixinBlockReinforcedFenceGate extends BlockFenceGate {

    // We're no-oping this method and instead adding the getMaterial override
    // Doesn't technically do the same thing as the original, but it's more stable and probably fine
    // If need be we could probably set the field based on the field name rather than the index
    // but no reflection is better than some reflection
    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/common/ObfuscationReflectionHelper;setPrivateValue(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;I)V"
        )
    )
    private void angelica$fixReflection(Class<?> clazz, Object instance, Object value, int fieldIndex) {}

    @Override
    public Material getMaterial() {
        return Material.iron;
    }
}
