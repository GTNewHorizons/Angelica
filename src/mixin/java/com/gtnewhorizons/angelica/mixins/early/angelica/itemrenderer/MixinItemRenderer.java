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

import com.gtnewhorizons.angelica.rendering.ItemRenderListManager;
import lombok.SneakyThrows;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.Tessellator;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer {
    @SneakyThrows
    @Inject(method = "renderItemIn2D",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private static void leFunnyRenderListStart(Tessellator tess, float a, float b, float c, float d, int e, int f, float g, CallbackInfo ci) {
        if (ItemRenderListManager.INSTANCE.pre(a, b, c, d, e, f, g)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItemIn2D",
        at = @At("RETURN"),
        require = 1)
    private static void leFunnyRenderListEnd(Tessellator tess, float a, float b, float c, float d, int e, int f, float g, CallbackInfo ci) {
        ItemRenderListManager.INSTANCE.post();
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
