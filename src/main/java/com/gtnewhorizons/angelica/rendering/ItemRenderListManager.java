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
import com.gtnewhorizon.gtnhlib.client.renderer.quad.QuadView;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import lombok.Data;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.List;

public class ItemRenderListManager {
    // Least used element is at position 0. This is in theory slightly faster.
    private static final Object2ObjectLinkedOpenHashMap<ItemProp, CachedVBO> vboCache = new Object2ObjectLinkedOpenHashMap<>(64);
    private static final ItemProp prop = new ItemProp();
    // Formula: (widthSubdivisions * 2 + heightSubdivisions * 2 + 2) * 4 * vertexSize
    // Using 256 as both variables due to enchants.
    private static ByteBuffer quadBuffer = BufferUtils.createByteBuffer(
        (1026 * DefaultVertexFormat.POSITION_TEXTURE_NORMAL.getVertexSize()) << 2
    );
    private static final Timer timer = Minecraft.getMinecraft().timer;
    // 50 seconds
    private static final int TICKS_CACHED = 10_000;

    public static VertexBuffer pre(float minU, float minV, float maxU, float maxV, int widthSubdivisions, int heightSubdivisions, float thickness) {
        prop.set(minU, minV, maxU, maxV, widthSubdivisions, heightSubdivisions, thickness);

        CachedVBO vbo = vboCache.getAndMoveToLast(prop);
        if (vbo != null) {
            vbo.render();
            return null;
        }

        if (!vboCache.isEmpty()) {
            final ItemProp oldestProp = vboCache.firstKey();
            final CachedVBO oldestVBO = vboCache.get(oldestProp);
            final long time = timer.elapsedTicks - TICKS_CACHED;
            if (time > oldestVBO.lastUsed) {
                // Clear the cache and use the first unused VertexBuffer and ItemProp
                vbo = vboCache.removeFirst();
                while (!vboCache.isEmpty() && time > vboCache.get(vboCache.firstKey()).lastUsed) {
                    vboCache.removeFirst().delete();
                }
                oldestProp.set(prop);
                vboCache.put(oldestProp, vbo);
            } else {
                if (vboCache.size() >= AngelicaConfig.itemRendererCacheSize) {

                    vbo = vboCache.removeFirst();
                    oldestProp.set(prop);
                    vboCache.put(oldestProp, vbo);
                } else {
                    vbo = new CachedVBO();
                    vboCache.put(new ItemProp(prop), vbo);
                }
            }
        } else {
            vbo = new CachedVBO();
            vboCache.put(new ItemProp(prop), vbo);
        }

        TessellatorManager.startCapturing();
        vbo.bind();

        return vbo.vertexBuffer;
    }

    public static void post(CapturingTessellator tessellator, VertexBuffer vbo) {
        final List<QuadView> quads = TessellatorManager.stopCapturingToPooledQuads();
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
        // Reset for later use
        quadBuffer.clear();

        tessellator.clearQuads();
        vbo.render();
    }

    private static final class CachedVBO {
        private VertexBuffer vertexBuffer;
        private int lastUsed;

        public CachedVBO() {
            this.vertexBuffer = new VertexBuffer(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, GL11.GL_QUADS);
        }

        public void render() {
            vertexBuffer.render();
            lastUsed = timer.elapsedTicks;
        }

        public void bind() {
            vertexBuffer.bind();
            lastUsed = timer.elapsedTicks;
        }

        public void delete() {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }

    @Data
    private static final class ItemProp {
        private float minU;
        private float minV;
        private float maxU;
        private float maxV;
        private int widthSubdivisions;
        private int heightSubdivisions;
        private float thickness;

        public ItemProp() {

        }

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
