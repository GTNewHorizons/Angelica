package com.gtnewhorizons.angelica.api.world;

import com.gtnewhorizons.angelica.AngelicaMod;
import net.minecraftforge.client.MinecraftForgeClient;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Draw hook inside the world pass for mod geometry. {@code RenderWorldLastEvent} fires after the pack's composite and
 * final passes, so geometry drawn there gets none of its lighting, fog or shadows.
 * <p>
 * Drawers run right after block entities, once per Forge render pass, with the block entity program bound like a TESR.
 * The modelview is the camera's, so draw relative to {@code TileEntityRendererDispatcher.staticPlayerX/Y/Z}. Restore any
 * GL state you change. Render thread only.
 */
public final class WorldDrawStage {

    @FunctionalInterface
    public interface Drawer {

        /** @param pass Forge render pass, 0 for solid and cutout, 1 for translucent */
        void draw(int pass, float partialTicks);
    }

    private static final CopyOnWriteArrayList<Drawer> drawers = new CopyOnWriteArrayList<>();

    private WorldDrawStage() {}

    public static void register(Drawer drawer) {
        drawers.addIfAbsent(drawer);
    }

    public static void unregister(Drawer drawer) {
        drawers.remove(drawer);
    }

    /** Called by the world renderer after block entities. A drawer that throws is removed. */
    public static void run(float partialTicks) {
        if (drawers.isEmpty()) return;
        final int pass = MinecraftForgeClient.getRenderPass();
        for (Drawer drawer : drawers) {
            try {
                drawer.draw(pass, partialTicks);
            } catch (Throwable t) {
                drawers.remove(drawer);
                AngelicaMod.LOGGER.error("World drawer {} threw, removing it", drawer, t);
            }
        }
    }
}
