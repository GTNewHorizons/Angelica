package me.jellysquid.mods.sodium.client.render.chunk.passes;

import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import it.unimi.dsi.fastutil.objects.Reference2IntArrayMap;


/**
 * Maps vanilla render layers to render passes used by Sodium. This provides compatibility with the render layers already
 * used by the base game.
 */
public class BlockRenderPassManager {
    private final Reference2IntArrayMap<RenderLayer> mappingsId = new Reference2IntArrayMap<>();

    public BlockRenderPassManager() {
        this.mappingsId.defaultReturnValue(-1);
    }

    public int getRenderPassId(RenderLayer layer) {
        int pass = this.mappingsId.getInt(layer);

        if (pass < 0) {
            throw new NullPointerException("No render pass exists for layer: " + layer);
        }

        return pass;
    }

    private void addMapping(RenderLayer layer, BlockRenderPass type) {
        if (this.mappingsId.put(layer, type.ordinal()) >= 0) {
            throw new IllegalArgumentException("Layer target already defined for " + layer);
        }
    }

    /**
     * Creates a set of render pass mappings to vanilla render layers which closely mirrors the rendering
     * behavior of vanilla.
     */
    public static BlockRenderPassManager createDefaultMappings() {
        BlockRenderPassManager mapper = new BlockRenderPassManager();
        mapper.addMapping(RenderLayer.solid(), BlockRenderPass.SOLID);
        mapper.addMapping(RenderLayer.cutoutMipped(), BlockRenderPass.CUTOUT_MIPPED);
        mapper.addMapping(RenderLayer.cutout(), BlockRenderPass.CUTOUT);
        mapper.addMapping(RenderLayer.translucent(), BlockRenderPass.TRANSLUCENT);
        mapper.addMapping(RenderLayer.tripwire(), BlockRenderPass.TRIPWIRE);

        return mapper;
    }
    public BlockRenderPass getRenderPassForLayer(RenderLayer layer) {
        return this.getRenderPass(this.getRenderPassId(layer));
    }

    public BlockRenderPass getRenderPass(int i) {
        return BlockRenderPass.VALUES[i];
    }
}
