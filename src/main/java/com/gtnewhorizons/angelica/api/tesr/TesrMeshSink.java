package com.gtnewhorizons.angelica.api.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

/** Capture-time sink. draw() captures instead of drawing */
public interface TesrMeshSink {

    Tessellator angelica$bucket(VertexFormat format, ResourceLocation texture, TesrMaterial material);

    default Tessellator angelica$bucket(VertexFormat format, ResourceLocation texture) {
        return angelica$bucket(format, texture, TesrMaterial.CURRENT_STATE);
    }

    default Tessellator angelica$bucket(VertexFormat format) {
        return angelica$bucket(format, null, TesrMaterial.CURRENT_STATE);
    }
}
