package com.gtnewhorizons.angelica.mixins.early.angelica.rendering;

import com.gtnewhorizons.angelica.render.SelectionBoxRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal_SelectionBox {

    /**
     * @author Angelica
     * @reason Optimize selection box rendering
     */
    @Overwrite
    public static void drawOutlinedBoundingBox(AxisAlignedBB aabb, int color) {
        SelectionBoxRenderer.draw(aabb, color);
    }
}
