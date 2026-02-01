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

package com.gtnewhorizons.angelica.mixins.early.angelica.itemrenderer;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.IVertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizons.angelica.rendering.ItemRenderListManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import net.minecraft.client.renderer.Tessellator;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer {

    @WrapMethod(method = "renderItemIn2D")
    private static void angelica$cacheItem(Tessellator tessellator, float minU, float minV, float maxU, float maxV, int widthSubdivisions, int heightSubdivisions, float thickness, Operation<Void> original) {
        final ItemRenderListManager.CachedVBO vbo = ItemRenderListManager.pre(minU, minV, maxU, maxV, widthSubdivisions, heightSubdivisions, thickness);
        if (vbo != null) {
            final DirectTessellator tess = TessellatorManager.startCapturingDirect();
            original.call(tess, minU, minV, maxU, maxV, widthSubdivisions, heightSubdivisions, thickness);
            ItemRenderListManager.post(tess, vbo);
        }
    }

    @Redirect(method = "renderItemIn2D",
        slice = @Slice(from = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;draw()I",
            ordinal = 0),
            to = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/Tessellator;startDrawingQuads()V",
                ordinal = 5)),
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;draw()I"),
        require = 5)
    private static int batchDrawCalls1(Tessellator instance) {
        return 0;
    }

    @Redirect(method = "renderItemIn2D",
        slice = @Slice(from = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;draw()I",
            ordinal = 0),
            to = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/Tessellator;startDrawingQuads()V",
                ordinal = 5)),
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;startDrawingQuads()V"),
        require = 5)
    private static void batchDrawCalls2(Tessellator instance) {

    }
}
