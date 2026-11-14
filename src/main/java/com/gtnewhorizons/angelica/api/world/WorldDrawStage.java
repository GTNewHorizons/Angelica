package com.gtnewhorizons.angelica.api.world;

import com.gtnewhorizons.angelica.AngelicaMod;
import net.minecraftforge.client.MinecraftForgeClient;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A spot in the world pass for mods that draw their own world geometry (moving structures, previews, effects).
 * <p>
 * Forge's {@code RenderWorldLastEvent} fires after Angelica has run the pack's composite and final passes, so anything
 * drawn there misses the pack's lighting, fog and shadows. Drawers registered here run right after block entities,
 * once per Forge render pass, with the pack's block entity program active like for a TESR. Without a pack they still
 * run there.
 * <p>
 * The modelview is the camera's like for TESRs, so draw relative to the camera
 * ({@code TileEntityRendererDispatcher.staticPlayerX/Y/Z}). Put back any GL state you change. Main thread only.
 */
public final class WorldDrawStage {

    /** Draws world geometry for one Forge render pass. */
    @FunctionalInterface
    public interface Drawer {

        /**
         * @param pass         Forge render pass, 0 for solid and cutout, 1 for translucent. Same as
         *                     {@code MinecraftForgeClient.getRenderPass()}
         * @param partialTicks the frame's partial ticks
         */
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

    /** Internal, called by Angelica's renderer after block entities. */
    public static void run(float partialTicks) {
        if (drawers.isEmpty()) return;
        final int pass = MinecraftForgeClient.getRenderPass();
        for (Drawer drawer : drawers) {
            try {
                drawer.draw(pass, partialTicks);
            } catch (Throwable t) {
                // One broken drawer shouldn't take the frame down or spam the log every frame
                drawers.remove(drawer);
                AngelicaMod.LOGGER.error("World drawer {} threw, removed it", drawer, t);
            }
        }
    }
}
