package com.gtnewhorizons.angelica.api.tesr;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.Tessellator;

/** Geometry for a part that keeps it outside {@code cubeList} */
@FunctionalInterface
public interface ModelPartMeshBuilder {

    void angelica$buildPart(Tessellator tess, ModelRenderer part, float scale);
}
