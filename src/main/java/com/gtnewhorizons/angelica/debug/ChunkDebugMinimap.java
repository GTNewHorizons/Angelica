/*
 * Adapated from: Beddium for usage in Angelica
 *
 * Copyright (C) 2025 Ven, FalsePattern
 * All Rights Reserved
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, only version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.gtnewhorizons.angelica.debug;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.debug.render.DebugDrawScope;
import com.gtnewhorizons.angelica.debug.render.DebugGeometry;
import com.gtnewhorizons.angelica.debug.render.DebugProgram;
import com.gtnewhorizons.angelica.debug.render.DebugPrograms;
import com.gtnewhorizons.angelica.debug.render.NativeScratch;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaRenderSectionManager;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Debug overlay that renders a minimap showing chunk loading and rendering state.
 */
public class ChunkDebugMinimap {
    private static final int RANGE = 32; // Chunks in each direction from player

    private static final int FLAGS = VertexFlags.COLOR_BIT;
    private static final VertexFormat FORMAT = DefaultVertexFormat.ALL_FORMATS[FLAGS];
    private static final int STRIDE = FORMAT.getVertexSize();
    private static final int COLOR_OFFSET = DebugGeometry.offsetOf(FORMAT, VertexFormatElement.Usage.COLOR);
    private static final int CHUNKS = RANGE * 2 * RANGE * 2;
    private static final int ALPHA = (int) (0.8F * 255.0F);
    private static final int BYTES = CHUNKS * 4 * STRIDE;
    private static final ChunkDebugMinimap INSTANCE = new ChunkDebugMinimap();
    private static volatile boolean enabled = false;

    private final DebugDrawScope scope = new DebugDrawScope();
    private final NativeScratch scratch = new NativeScratch(BYTES);
    private final Matrix4f mvp = new Matrix4f();
    private long cursor;

    public static synchronized void enable() {
        if (enabled) return;
        enabled = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    public static synchronized void disable() {
        if (!enabled) return;
        enabled = false;
        MinecraftForge.EVENT_BUS.unregister(INSTANCE);
        INSTANCE.scratch.free();
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        final CeleritasWorldRenderer renderer;
        try {
            renderer = CeleritasWorldRenderer.getInstance();
        } catch (IllegalStateException e) {
            return; // Renderer not initialized
        }

        final AngelicaRenderSectionManager manager = renderer.getRenderSectionManager();
        if (manager == null) return;

        final float partialTicks = event.partialTicks;
        final double pX = mc.thePlayer.prevPosX + (mc.thePlayer.posX - mc.thePlayer.prevPosX) * partialTicks;
        final double pZ = mc.thePlayer.prevPosZ + (mc.thePlayer.posZ - mc.thePlayer.prevPosZ) * partialTicks;

        final int playerChunkX = ((int) Math.floor(pX)) >> 4;
        final int playerChunkZ = ((int) Math.floor(pZ)) >> 4;

        scratch.ensure(BYTES);
        cursor = scratch.address();
        final ChunkProviderClient chunkProvider = (ChunkProviderClient) mc.theWorld.getChunkProvider();

        for (int x = playerChunkX - RANGE; x < playerChunkX + RANGE; x++) {
            for (int z = playerChunkZ - RANGE; z < playerChunkZ + RANGE; z++) {
                drawChunk(x, z, playerChunkX, playerChunkZ, manager, chunkProvider);
            }
        }

        GLSMMatrices.getProjectionMatrix().mul(GLSMMatrices.getModelViewMatrix(), mvp).scale(2, 2, 1).translate(32, 32, 0);

        scope.beginHud();
        try {
            final DebugProgram color = DebugPrograms.color();
            color.use();
            color.mvp(mvp);
            final int bytes = (int) (cursor - scratch.address());
            TessellatorStreamingDrawer.drawPacked(scratch.window(bytes), GL11.GL_QUADS, FLAGS, bytes / STRIDE);
        } finally {
            scope.end();
        }
    }

    private void drawChunk(int chunkX, int chunkZ, int playerChunkX, int playerChunkZ, AngelicaRenderSectionManager manager, ChunkProviderClient chunkProvider) {
        boolean initialized = false;
        float r = 0, g = 0, b = 0;

        // Check all 16 sections in the chunk column
        for (int y = 0; y < 16; y++) {
            if (manager.isSectionBuilt(chunkX, y, chunkZ) && !manager.isSectionVisuallyEmpty(chunkX, y, chunkZ)) {
                initialized = true;
                g = 1; // Green = built
                if (manager.isSectionVisible(chunkX, y, chunkZ)) {
                    b = 1; // Cyan = visible
                    break;
                }
            }
        }

        if (!initialized) {
            // Check if chunk is loaded
            final var chunk = chunkProvider.provideChunk(chunkX, chunkZ);
            if (chunk instanceof EmptyChunk) {
                r = 1; // Red = unloaded
            } else {
                b = 1; // Blue = loaded but not built
            }
        }

        final int argb = ALPHA << 24 | (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);

        final int xStart = chunkX - playerChunkX;
        final int zStart = chunkZ - playerChunkZ;
        final int xEnd = xStart + 1;
        final int zEnd = zStart + 1;

        vertex(xStart, zStart, argb);
        vertex(xStart, zEnd, argb);
        vertex(xEnd, zEnd, argb);
        vertex(xEnd, zStart, argb);
    }

    private void vertex(float x, float y, int argb) {
        final long p = cursor;
        DebugGeometry.putPosition(p, x, y, 0.0F);
        DebugGeometry.putColor(p + COLOR_OFFSET, argb);
        cursor = p + STRIDE;
    }
}
