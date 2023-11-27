package com.gtnewhorizons.angelica.rendering;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public class AngelicaBlockRenderingHandler implements ISimpleBlockRenderingHandler {
    private final ISimpleBlockRenderingHandler delegate;
    private static final Map<ISimpleBlockRenderingHandler, ISimpleBlockRenderingHandler> HANDLERS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Logger LOGGER = LogManager.getLogger("Angelica");

    public static ISimpleBlockRenderingHandler forHandler(ISimpleBlockRenderingHandler delegate) {
        return HANDLERS.computeIfAbsent(delegate, h -> {
            // TODO add whitelist
            LOGGER.warn("Renderer {} will be synchronized, which may degrade performance", h.getClass().getName());
            return new AngelicaBlockRenderingHandler(h);
        });
    }

    private AngelicaBlockRenderingHandler(ISimpleBlockRenderingHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        synchronized (AngelicaBlockRenderingHandler.class) {
            this.delegate.renderInventoryBlock(block, metadata, modelId, renderer);
        }
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks renderer) {
        synchronized (AngelicaBlockRenderingHandler.class) {
            return this.delegate.renderWorldBlock(world, x, y, z, block, modelId, renderer);
        }
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        synchronized (AngelicaBlockRenderingHandler.class) {
            return this.delegate.shouldRender3DInInventory(modelId);
        }
    }

    @Override
    public int getRenderId() {
        return this.delegate.getRenderId();
    }
}
