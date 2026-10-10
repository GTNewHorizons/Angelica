package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuad;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.rendering.tesr.DrawState;
import com.gtnewhorizons.angelica.shadercompat.ShaderGlint;
import net.coderbot.iris.layer.PassOverride;
import net.minecraft.util.ResourceLocation;

import java.util.List;

public final class BufferSourceProbe {

    private static final TesrMaterial TRANSLUCENT_MATERIAL = TesrMaterial.builder().translucent().build();

    private BufferSourceProbe() {}

    public static List<RenderLayer> prepare(AngelicaBufferSource source) {
        return source.prepare();
    }

    public static List<BufferSegment> segmentsFor(AngelicaBufferSource source, RenderLayer layer) {
        return source.segmentsFor(layer);
    }

    public static RenderLayer layer(String name, TransparencyType transparencyType) {
        final TesrMaterial material = transparencyType == TransparencyType.OPAQUE ? TesrMaterial.CURRENT_STATE : TRANSLUCENT_MATERIAL;
        return RenderLayer.tesr(new ResourceLocation("angelicatest", name), material, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, DrawState.DISABLED, false, true);
    }

    public static ModelQuad quad() {
        final ModelQuad q = new ModelQuad();
        for (int i = 0; i < 4; i++) {
            q.setX(i, i);
            q.setY(i, i);
            q.setZ(i, i);
        }
        return q;
    }
}
