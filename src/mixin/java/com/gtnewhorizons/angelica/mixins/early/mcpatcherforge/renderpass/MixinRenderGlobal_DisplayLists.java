package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.renderpass;

import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * RenderPass display list allocation increase - increases from 3 to 5 display lists per chunk.
 * Only the constructor modification - separated for compatibility with Sodium.
 */
@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal_DisplayLists {

    @Redirect(
        method = "<init>(Lnet/minecraft/client/Minecraft;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GLAllocation;generateDisplayLists(I)I", ordinal = 0))
    private int modifyRenderGlobal(int n) {
        return GLAllocation.generateDisplayLists(n / 3 * 5);
    }
}
