package com.gtnewhorizons.angelica.rendering.tesr;

import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.Tessellator;

import java.util.List;

public final class ModelPartMesher {

    private ModelPartMesher() {}

    public static void emitPart(Tessellator t, ModelRenderer part, float scale) {
        t.setTranslation(
            part.offsetX + part.rotationPointX * scale,
            part.offsetY + part.rotationPointY * scale,
            part.offsetZ + part.rotationPointZ * scale);
        emitPartLocal(t, part, scale);
        t.setTranslation(0, 0, 0);
    }

    public static void emitPartLocal(Tessellator t, ModelRenderer part, float scale) {
        final List<ModelBox> boxes = part.cubeList;
        for (int i = 0, n = boxes.size(); i < n; i++) {
            boxes.get(i).render(t, scale);
        }
    }
}
