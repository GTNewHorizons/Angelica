package com.gtnewhorizons.angelica.api.tesr;

import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import net.minecraft.client.model.ModelRenderer;

/** For parts that override {@code ModelRenderer.render} */
public final class ModelPartBatch {

    private ModelPartBatch() {}

    /** @return false if it cannot be batched */
    public static boolean partDraw(ModelRenderer part, float scale) {
        return ModelPartBatcher.partDraw(part, scale, null);
    }

    public static boolean partDraw(ModelRenderer part, float scale, ModelPartMeshBuilder builder) {
        return ModelPartBatcher.partDraw(part, scale, builder);
    }
}
