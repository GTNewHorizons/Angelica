/*
 * This file is part of FalseTweaks.
 *
 * Copyright (C) 2022-2024 FalsePattern
 * All Rights Reserved
 *
 * Modifications by Angelica in accordance with LGPL v3.0
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * FalseTweaks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FalseTweaks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FalseTweaks. If not, see <https://www.gnu.org/licenses/>.
 */

package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.nio.ByteBuffer;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public class ItemRenderListManager {
    // Least used element is at position 0. This is in theory slightly faster.
    private static final Object2ObjectLinkedOpenHashMap<ItemProp, CachedVBO> vboCache = new Object2ObjectLinkedOpenHashMap<>(64);

    // Formula: (widthSubdivisions * 2 + heightSubdivisions * 2 + 2) * 4 * vertexSize
    // Using 256 as both variables due to enchants, to prevent later re-allocations.
    private static ByteBuffer quadBuffer = BufferUtils.createByteBuffer(
        (1026 * DefaultVertexFormat.POSITION_TEXTURE_NORMAL.getVertexSize()) << 2
    );

    // 1 minute
    private static final int EXPIRY_TICKS = 1_200;
    private static int smallestExpiry;

    private static final ItemProp prop = new ItemProp();

    public static VertexBuffer pre(float minU, float minV, float maxU, float maxV, int widthSubdivisions, int heightSubdivisions, float thickness) {
        prop.set(minU, minV, maxU, maxV, widthSubdivisions, heightSubdivisions, thickness);

        if (!vboCache.isEmpty()) {

            final CachedVBO vbo = vboCache.getAndMoveToLast(prop);

            if (vbo != null) {
                final int time = getElapsedTicks();
                vbo.render(time);

                // Prevent constant map lookups by only storing the expiry of the least recently used entry
                if (time > smallestExpiry && time > (smallestExpiry = (vboCache.get(vboCache.firstKey()).expiry + 20))) {
                    vboCache.removeFirst().delete();
                    if (!vboCache.isEmpty()) {
                        smallestExpiry = vboCache.get(vboCache.firstKey()).expiry + 20;
                    }
                }

                return null;
            }
        }

        final CachedVBO vbo;
        if (vboCache.size() >= AngelicaConfig.itemRendererCacheSize) {
            final ItemProp oldestProp = vboCache.firstKey();
            vbo = vboCache.removeFirst();
            oldestProp.set(prop);
            vboCache.put(oldestProp, vbo);
        } else {
            vbo = new CachedVBO();
            vboCache.put(new ItemProp(prop), vbo);
        }

        vbo.expiry = getElapsedTicks() + EXPIRY_TICKS;

        TessellatorManager.startCapturing();

        return vbo.vertexBuffer;
    }

    public static void post(CapturingTessellator tessellator, VertexBuffer vbo) {
        final var quads = TessellatorManager.stopCapturingToPooledQuads();
        final int size = quads.size();

        final int needed = (DefaultVertexFormat.POSITION_TEXTURE_NORMAL.getVertexSize() * size) << 2;
        if (quadBuffer.capacity() < needed) {
            quadBuffer = BufferUtils.createByteBuffer(HashCommon.nextPowerOfTwo(needed));
        }

        for (int i = 0; i < size; i++) {
            DefaultVertexFormat.POSITION_TEXTURE_NORMAL.writeQuad(quads.get(i), quadBuffer);
        }

        quadBuffer.flip();
        vbo.upload(quadBuffer);
        quadBuffer.clear();

        tessellator.clearQuads();
        vbo.render();
    }

    private static int getElapsedTicks() {
        return Minecraft.getMinecraft().thePlayer.ticksExisted;
    }

    private static final class CachedVBO {
        private VertexBuffer vertexBuffer;
        private int expiry;

        public CachedVBO() {
            this.vertexBuffer = new VertexBuffer(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, GL11.GL_QUADS);
        }

        private void render(int elapsedTicks) {
            vertexBuffer.render();
            expiry = elapsedTicks + EXPIRY_TICKS;
        }

        private void delete() {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }

    @NoArgsConstructor
    @Data
    private static final class ItemProp {
        private float minU;
        private float minV;
        private float maxU;
        private float maxV;
        private int widthSubdivisions;
        private int heightSubdivisions;
        private float thickness;

        public ItemProp(ItemProp old) {
            set(
                old.minU, old.minV,
                old.maxU, old.maxV,
                old.widthSubdivisions, old.heightSubdivisions,
                old.thickness
            );
        }

        public void set(ItemProp other) {
            set(
                other.minU, other.minV,
                other.maxU, other.maxV,
                other.widthSubdivisions, other.heightSubdivisions,
                other.thickness
            );
        }

        public void set(float minU, float minV, float maxU, float maxV, int widthSubdivisions, int heightSubdivisions, float thickness) {
            this.minU = minU;
            this.minV = minV;
            this.maxU = maxU;
            this.maxV = maxV;
            this.widthSubdivisions = widthSubdivisions;
            this.heightSubdivisions = heightSubdivisions;
            this.thickness = thickness;
        }
    }
}
