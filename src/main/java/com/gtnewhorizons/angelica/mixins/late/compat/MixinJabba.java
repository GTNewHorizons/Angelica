package com.gtnewhorizons.angelica.mixins.late.compat;

import mcp.mobius.betterbarrels.client.ClientProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = ClientProxy.class, remap = false)
public class MixinJabba {

    /**
     * @author mitchej123
     * @reason We're redirecting their renderer so don't blow up
     */
    @Overwrite
    public void registerRenderers() {
        // Do nothing
    }
}
