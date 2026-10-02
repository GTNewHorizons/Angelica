package com.gtnewhorizons.angelica.rendering.celeritas.threading;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.gtnewhorizons.angelica.api.ThreadSafeISBRH;
import com.gtnewhorizons.angelica.api.ThreadSafeISBRHFactory;
import com.gtnewhorizons.angelica.rendering.IsbrhDispatch;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    private abstract static class FakeHandler implements ISimpleBlockRenderingHandler {

        @Override
        public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {}

        @Override
        public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks renderer) {
            return false;
        }

        @Override
        public boolean shouldRender3DInInventory(int modelId) {
            return false;
        }

        @Override
        public int getRenderId() {
            return 0;
        }
    }

    private static class SharedHandler extends FakeHandler {}

    @ThreadSafeISBRH(perThread = true)
    public static class PerThreadHandler extends FakeHandler {}

    private static class FactoryHandler extends FakeHandler implements ThreadSafeISBRHFactory {

        @Override
        public ThreadSafeISBRHFactory newInstance() {
            return new FactoryHandler();
        }
    }

    private Map<Integer, ISimpleBlockRenderingHandler> handlers;

    @BeforeEach
    void resetDispatch() {
        handlers = new HashMap<>();
        IsbrhDispatch.invalidate();
    }

    @Test
    void sharedHandlerReturnedOnBothPaths() throws InterruptedException {
        SharedHandler shared = new SharedHandler();
        handlers.put(1, shared);

        assertSame(shared, IsbrhDispatch.resolve(handlers, 1, true));

        AtomicReference<ISimpleBlockRenderingHandler> offThread = new AtomicReference<>();
        Thread t = new Thread(() -> offThread.set(IsbrhDispatch.resolve(handlers, 1, false)));
        t.start();
        t.join();
        assertSame(shared, offThread.get());
    }

    @Test
    void perThreadHandlerIsStableAcrossIdsAndDiffersOnASpawnedThread() throws InterruptedException {
        PerThreadHandler main = new PerThreadHandler();
        handlers.put(2, main);
        handlers.put(3, main);

        ISimpleBlockRenderingHandler first = IsbrhDispatch.resolve(handlers, 2, false);
        ISimpleBlockRenderingHandler second = IsbrhDispatch.resolve(handlers, 2, false);
        assertNotSame(main, first);
        assertSame(first, second);

        ISimpleBlockRenderingHandler sameClassOtherId = IsbrhDispatch.resolve(handlers, 3, false);
        assertSame(first, sameClassOtherId);

        AtomicReference<ISimpleBlockRenderingHandler> spawned = new AtomicReference<>();
        Thread t = new Thread(() -> spawned.set(IsbrhDispatch.resolve(handlers, 2, false)));
        t.start();
        t.join();
        assertNotSame(first, spawned.get());
    }

    @Test
    void factoryHandlerUsesNewInstance() {
        FactoryHandler main = new FactoryHandler();
        handlers.put(4, main);

        ISimpleBlockRenderingHandler adapted = IsbrhDispatch.resolve(handlers, 4, false);
        assertNotSame(main, adapted);
        assertInstanceOf(FactoryHandler.class, adapted);
    }

    @Test
    void missingIdResolvesToNull() {
        assertNull(IsbrhDispatch.resolve(handlers, 99, true));
        assertNull(IsbrhDispatch.resolve(handlers, 99, false));
    }

    @Test
    void putThenInvalidateMakesNewRegistrationResolvable() {
        assertNull(IsbrhDispatch.resolve(handlers, 5, true));

        SharedHandler shared = new SharedHandler();
        handlers.put(5, shared);
        IsbrhDispatch.invalidate();

        assertSame(shared, IsbrhDispatch.resolve(handlers, 5, true));
    }

    @Test
    void replaceThenInvalidateReturnsNewHandler() {
        SharedHandler first = new SharedHandler();
        handlers.put(6, first);
        assertSame(first, IsbrhDispatch.resolve(handlers, 6, true));

        SharedHandler second = new SharedHandler();
        handlers.put(6, second);
        IsbrhDispatch.invalidate();
        assertSame(second, IsbrhDispatch.resolve(handlers, 6, true));
    }
}
