package com.gtnewhorizons.angelica.mixins.early.celeritas.threading;

import com.gtnewhorizons.angelica.rendering.celeritas.threading.RenderPassHelper;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Makes ForgeHooksClient.getWorldRenderPass() thread-safe by delegating to RenderPassHelper.
 */
@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class MixinForgeHooksClient {
    /**
     * @author Angelica
     * @reason Thread-safe worldRenderPass for multi-threaded chunk building
     */
    @Overwrite
    public static int getWorldRenderPass() {
        return RenderPassHelper.getWorldRenderPass();
    }
}
