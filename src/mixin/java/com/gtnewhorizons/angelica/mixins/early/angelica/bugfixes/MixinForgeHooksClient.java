package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = ForgeHooksClient.class, remap = false)
public class MixinForgeHooksClient {
    /**
     * @author mitchej123
     * @reason thread safe world render pass
     */
    @Overwrite
    public static int getWorldRenderPass() {
        return ChunkRenderManager.getWorldRenderPass();
    }


}
