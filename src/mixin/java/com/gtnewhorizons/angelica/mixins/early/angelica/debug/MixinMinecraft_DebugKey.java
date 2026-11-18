package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.debug.DebugKeyHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.input.Keyboard;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class MixinMinecraft_DebugKey {

    @WrapOperation(
        method = "runTick",
        at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/minecraft/client/settings/GameSettings;showDebugInfo:Z")
    )
    private void angelica$deferDebugInfoToggle(GameSettings instance, boolean value, Operation<Void> original) {
        if (!AngelicaConfig.modernizeF3Screen) {
            original.call(instance, value);
            return;
        }
        DebugKeyHandler.F3_TOGGLE.arm(GuiScreen.isShiftKeyDown(), Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU));
    }

    @WrapOperation(
        method = "runTick",
        at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/minecraft/client/settings/GameSettings;showDebugProfilerChart:Z")
    )
    private void angelica$deferDebugProfilerChart(GameSettings instance, boolean value, Operation<Void> original) {
        if (!AngelicaConfig.modernizeF3Screen) {
            original.call(instance, value);
        }
    }
}
