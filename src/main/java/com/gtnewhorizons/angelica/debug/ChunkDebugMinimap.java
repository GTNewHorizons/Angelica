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

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaRenderSectionManager;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.opengl.GL11;

/**
 * Debug overlay that renders a minimap showing chunk loading and rendering state. Toggle via /angelica minimap
 */
public class ChunkDebugMinimap {
    private static final ChunkDebugMinimap INSTANCE = new ChunkDebugMinimap();
    @Getter
    private static volatile boolean enabled = false;

    private static final int RANGE = 32; // Chunks in each direction from player

    public static synchronized void toggle() {
        if (enabled) disable();
        else enable();
    }

    public static synchronized void enable() {
        if (enabled) return;
        enabled = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    public static synchronized void disable() {
        if (!enabled) return;
        enabled = false;
        MinecraftForge.EVENT_BUS.unregister(INSTANCE);
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

        // Save GL state
        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLStateManager.glPushMatrix();

        // Setup rendering
        GLStateManager.glScaled(2, 2, 1);
        GLStateManager.glTranslated(32, 32, 0);

        GLStateManager.disableLighting();
        GLStateManager.disableTexture();
        GLStateManager.disableDepthTest();
        GLStateManager.enableBlend();
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        final Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();

        final ChunkProviderClient chunkProvider = (ChunkProviderClient) mc.theWorld.getChunkProvider();

        for (int x = playerChunkX - RANGE; x < playerChunkX + RANGE; x++) {
            for (int z = playerChunkZ - RANGE; z < playerChunkZ + RANGE; z++) {
                drawChunk(tess, x, z, playerChunkX, playerChunkZ, manager, chunkProvider);
            }
        }

        tess.draw();

        // Restore GL state
        GLStateManager.enableLighting();
        GLStateManager.enableDepthTest();
        GLStateManager.disableBlend();
        GLStateManager.glPopMatrix();
        GLStateManager.glPopAttrib();
    }

    private void drawChunk(Tessellator tess, int chunkX, int chunkZ, int playerChunkX, int playerChunkZ, AngelicaRenderSectionManager manager, ChunkProviderClient chunkProvider) {
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

        tess.setColorRGBA_F(r, g, b, 0.8f);

        final int xStart = chunkX - playerChunkX;
        final int zStart = chunkZ - playerChunkZ;
        final int xEnd = xStart + 1;
        final int zEnd = zStart + 1;

        tess.addVertex(xStart, zStart, 0);
        tess.addVertex(xStart, zEnd, 0);
        tess.addVertex(xEnd, zEnd, 0);
        tess.addVertex(xEnd, zStart, 0);
    }
}
