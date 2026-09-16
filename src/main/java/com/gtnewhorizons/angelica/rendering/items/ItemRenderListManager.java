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

package com.gtnewhorizons.angelica.rendering.items;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IndexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCalloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;

public class ItemRenderListManager {

    private static final ItemPropCache<CachedVBO> vboCache = new ItemPropCache<>(() -> AngelicaConfig.itemRendererCacheSize, CachedVBO::new, CachedVBO::delete);

    public static CachedVBO pre(float minU, float minV, float maxU, float maxV, int widthSubdivisions, int heightSubdivisions, float thickness) {
        final CachedVBO hit = vboCache.get(minU, minV, maxU, maxV, widthSubdivisions, heightSubdivisions, thickness);
        if (hit != null) {
            hit.render();
            return null;
        }
        return vboCache.insert();
    }

    public static void post(DirectTessellator tessellator, CachedVBO vbo) {
        vbo.allocate(tessellator);
        TessellatorManager.stopCapturingDirect();
        vbo.vertexBuffer.render();
    }

    public static void registerReloadListener(){
        IReloadableResourceManager resourceManager = (IReloadableResourceManager) Minecraft.getMinecraft()
            .getResourceManager();
        resourceManager.registerReloadListener(_ -> clearCache());
    }

    public static void clearCache() {
        vboCache.clear();
    }

    public static final class CachedVBO {
        private final IVertexArrayObject vertexBuffer;
        private final IndexBuffer ebo;
        private int extVbo = 0;

        public CachedVBO() {
            this.ebo = new IndexBuffer();
            this.vertexBuffer = VAOManager.createMutableVAO(
                DefaultVertexFormat.POSITION_TEXTURE_NORMAL,
                GL11.GL_TRIANGLES,
                ebo
            );
        }

        private void allocate(DirectTessellator tessellator) {
            tessellator.allocateToVBO(vertexBuffer, ebo);
            attachExtAttribs(tessellator);
        }

        private void attachExtAttribs(DirectTessellator tessellator) {
            final ImmediateExtendedAttribHandler handler = GLSMHooks.immediateExtendedHandler;
            final VertexFormat format = tessellator.getVertexFormat();
            final int vertexCount = tessellator.getVertexCount();
            final int extPrim = handler == null ? 0 : ImmediateExtendedAttribHandler.extPrimVerts(tessellator.getDrawMode(), vertexCount);
            final boolean capture = handler != null && format != null && format.hasTexture()
                && extPrim != 0
                && (extVbo != 0 || handler.wantsExtendedCapture());
            if (!capture) {
                detachExtAttribs();
                return;
            }

            final int extStride = ImmediateExtendedAttribHandler.EXT_STRIDE;
            final int stride = format.getVertexSize();
            final int texOffset = ImmediateExtendedAttribHandler.texOffset(format);
            final int normalOffset = ImmediateExtendedAttribHandler.normalOffset(format);
            final ByteBuffer packed = tessellator.getWriteBuffer();
            final long srcBase = memAddress0(packed) + packed.position();

            final ByteBuffer ext = memCalloc(vertexCount, extStride);
            handler.buildPacked(srcBase, stride, 0, texOffset, normalOffset, vertexCount, extPrim, memAddress0(ext), extStride);

            final boolean firstAttach = extVbo == 0;
            if (firstAttach) {
                extVbo = GLStateManager.glGenBuffers();
            }
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, extVbo);
            ext.position(0).limit(vertexCount * extStride);
            GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, ext, GL15.GL_STATIC_DRAW);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            memFree(ext);

            if (firstAttach) {
                vertexBuffer.bind();
                GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, extVbo);
                ImmediateExtendedAttribHandler.setupExtAttribPointers(0L, extStride);
                GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                vertexBuffer.unbind();
            }
        }

        private void detachExtAttribs() {
            if (extVbo == 0) return;
            vertexBuffer.bind();
            GLStateManager.glDisableVertexAttribArray(ImmediateExtendedAttribHandler.LOC_MID_TEX);
            GLStateManager.glDisableVertexAttribArray(ImmediateExtendedAttribHandler.LOC_TANGENT);
            vertexBuffer.unbind();
            GLStateManager.glDeleteBuffers(extVbo);
            extVbo = 0;
        }

        private void render() {
            vertexBuffer.render();
        }

        private void delete() {
            vertexBuffer.delete();
            // EBO gets deleted by vertexBuffer.delete()
            if (extVbo != 0) {
                GLStateManager.glDeleteBuffers(extVbo);
                extVbo = 0;
            }
        }
    }

}
