package jss.notfine.mixins.early.minecraft.toggle;

import jss.notfine.core.SettingsManager;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
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
    private boolean notFine$toggleWaterDetail(GameSettings settings) {
        return SettingsManager.waterDetail;
    }

}
