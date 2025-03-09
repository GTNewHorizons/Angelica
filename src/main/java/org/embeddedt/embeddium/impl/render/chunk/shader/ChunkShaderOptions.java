package org.embeddedt.embeddium.impl.render.chunk.shader;

import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.render.ShaderModBridge;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;

public record ChunkShaderOptions(ChunkFogMode fog, TerrainRenderPass pass, ChunkVertexType vertexType) {

    public ShaderConstants constants() {
        ShaderConstants.Builder constants = ShaderConstants.builder();
        constants.addAll(this.fog.getDefines());

        if (this.pass.supportsFragmentDiscard()) {
            constants.add("USE_FRAGMENT_DISCARD");
        }

        constants.addAll(this.vertexType.getDefines());

        constants.add("VERT_POS_SCALE", String.valueOf(this.vertexType.getPositionScale()));
        constants.add("VERT_POS_OFFSET", String.valueOf(this.vertexType.getPositionOffset()));
        constants.add("VERT_TEX_SCALE", String.valueOf(this.vertexType.getTextureScale()));

        if(!ShaderModBridge.emulateLegacyColorBrightnessFormat()) {
            constants.add("USE_VANILLA_COLOR_FORMAT");
        }

        return constants.build();
    }
}
