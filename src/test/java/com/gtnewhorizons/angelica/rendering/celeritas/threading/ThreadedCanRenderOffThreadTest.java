package com.gtnewhorizons.angelica.rendering.celeritas.threading;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadedCanRenderOffThreadTest {

    private static class RenderTypeBlock extends Block {
        private final int renderType;

        RenderTypeBlock(int renderType) {
            super(Material.rock);
            this.renderType = renderType;
        }

        @Override
        public int getRenderType() {
            return renderType;
        }
    }

    @Test
    void invisibleAndVanillaRenderTypesRenderOffThread() {
        final ThreadedAngelicaChunkBuilderMeshingTask task = new ThreadedAngelicaChunkBuilderMeshingTask(new RenderSection(null, 0, 0, 0), null, 0, new Vector3d());
        assertTrue(task.canRenderOffThread(new RenderTypeBlock(-1)));
        assertTrue(task.canRenderOffThread(new RenderTypeBlock(0)));
        assertTrue(task.canRenderOffThread(new RenderTypeBlock(41)));
    }
}
