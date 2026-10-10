package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.ffp.CubeParams;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.shadercompat.ShaderGlint;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.coderbot.iris.layer.PassOverride;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.COLOR_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.VERTEX_SIZE;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.X_INDEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class RetainedTesrTestSupport {

    private RetainedTesrTestSupport() {}

    static final TesrMaterial STREAM = TesrMaterial.builder().stream().build();

    static RenderLayer testLayer() {
        return RenderLayer.tesr(null, TesrMaterial.CURRENT_STATE, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, DrawState.DISABLED, false, true);
    }

    static TemplateBuffer template() {
        final int[] data = new int[4 * VERTEX_SIZE];
        for (int v = 0; v < 4; v++) {
            final int base = v * VERTEX_SIZE;
            data[base + X_INDEX] = Float.floatToRawIntBits(v);
            data[base + COLOR_INDEX] = 0xFFFFFFFF;
        }
        return new TemplateBuffer(data, 4, GL11.GL_QUADS);
    }

    static CubeParams[] cubes(int count) {
        final CubeParams[] params = new CubeParams[count];
        for (int i = 0; i < count; i++) {
            params[i] = CubeParams.of(64f, 32f, i % 2 == 1, i * 8, 0, -4f, -4f, -2f, 8, 8, 4, 0f);
            assertNotNull(params[i], "cube " + i + " must pack");
        }
        return params;
    }

    static RetainedTesrGroups.Group onlyGroup(RetainedTesrGroups groups) {
        final Reference2ObjectOpenHashMap<RenderLayer, ObjectArrayList<RetainedTesrGroups.Group>> byLayer = Reflect.get(groups, "byLayer");
        assertEquals(1, byLayer.size());
        final ObjectArrayList<RetainedTesrGroups.Group> list = byLayer.values().iterator().next();
        assertEquals(1, list.size());
        return list.get(0);
    }

    static int liveCull(TesrMaterial material) {
        return material.isNoCull() ? DrawState.DISABLED : DrawState.liveCull();
    }

    static RenderLayer capturedLayer(TesrMaterial material) {
        return Reflect.invoke(ModelPartBatcher.INSTANCE, "layerFor",
            new Class<?>[] { ResourceLocation.class, TesrMaterial.class, PassOverride.class, float.class, float.class, int.class, int.class, boolean.class, boolean.class },
            null, material, PassOverride.NONE, 0f, 0f, ShaderGlint.NO_TINT, liveCull(material), DrawState.liveLit(material), DrawState.liveLightmap());
    }

    static final class CountingPipeline implements TesrInstancingPipeline {

        private final boolean matrixVariant;
        private final boolean cubeVariant;
        int rebinds;
        int matrixBinds;
        int cubeBinds;

        CountingPipeline(boolean matrixVariant, boolean cubeVariant) {
            this.matrixVariant = matrixVariant;
            this.cubeVariant = cubeVariant;
        }

        @Override
        public void rebindCurrentPass() {
            rebinds++;
        }

        @Override
        public boolean hasInstancedVariant(Instancing kind) {
            return switch (kind) {
                case TEMPLATE -> matrixVariant;
                case CUBE -> cubeVariant;
                default -> false;
            };
        }

        @Override
        public void bindInstancedVariant(Instancing kind) {
            switch (kind) {
                case TEMPLATE -> matrixBinds++;
                case CUBE -> cubeBinds++;
                default -> throw new AssertionError(kind);
            }
        }
    }
}
