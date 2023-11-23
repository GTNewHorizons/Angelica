package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.mixins.interfaces.IHasTessellator;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderBlocks.class)
public class MixinRenderBlocks implements IHasTessellator {
    // Sodium Tesselator will be non null if we are not on the main thread
    private Tessellator sodium$tessellator = null;

    @Override
    public Tessellator getTessellator() {
        return sodium$tessellator;
    }

    @Inject(method = {"<init>()V","<init>(Lnet/minecraft/world/IBlockAccess;)V"}, at = @At("TAIL"))
    private void initTessellator(CallbackInfo ci) {
        if(Thread.currentThread() != SodiumClientMod.getMainThread()) {
            sodium$tessellator = new Tessellator();
        }
    }

    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/Tessellator;instance:Lnet/minecraft/client/renderer/Tessellator;"))
    private Tessellator modifyTessellatorAccess() {
        return sodium$tessellator != null ? sodium$tessellator : Tessellator.instance;
    }

}