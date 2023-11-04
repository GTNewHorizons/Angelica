package me.jellysquid.mods.sodium.mixin.features.chunk_rendering;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(RenderLayer.class)
public class MixinRenderLayer {
    @Unique
    private static final List<RenderLayer> embeddium$blockLayers = ImmutableList.of(RenderLayer.getSolid(), RenderLayer.getCutoutMipped(), RenderLayer.getCutout(), RenderLayer.getTranslucent(), RenderLayer.getTripwire());

    /**
     * @author Kasualix
     * @reason Don't create an immutableList every time this is called.
     */
    @Overwrite
    public static List<RenderLayer> getBlockLayers() {
        return embeddium$blockLayers;
    }
}
