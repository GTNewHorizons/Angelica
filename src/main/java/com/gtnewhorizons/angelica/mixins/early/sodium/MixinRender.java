package com.gtnewhorizons.angelica.mixins.early.sodium;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Render.class)
public class MixinRender {

    @Redirect(method = "doRenderShadowAndFire", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;fancyGraphics:Z", opcode = Opcodes.GETFIELD, ordinal = 0))
    private boolean redirectGetFancyShadows(GameSettings settings) {
        return SodiumClientMod.options().quality.entityShadows.isFancy();
    }

}
