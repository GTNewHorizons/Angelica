package org.embeddedt.embeddium.impl.render.chunk.terrain.material;

import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.MaterialParameters;

import java.util.Objects;

/**
 * A material provides the full configuration about how a geometry element should render. It corresponds to the vanilla
 * RenderType configured for a block. Material configuration is encoded alongside the rest of the vertex data, which
 * allows for multiple vanilla RenderTypes to be consolidated into a single terrain render pass on the CPU for greater
 * efficiency. The material configuration is recovered on the GPU within the render pass.
 */
public final class Material {
    public final TerrainRenderPass pass;
    public final int packed;

    public final AlphaCutoffParameter alphaCutoff;
    public final boolean mipped;

    /**
     * Constructs a new Material.
     * @param pass the {@link TerrainRenderPass} to use for the base configuration
     * @param alphaCutoff the alpha level below which fragments should be discarded (only respected if
     *                    {@link TerrainRenderPass#supportsFragmentDiscard()} is true for the given pass)
     * @param mipped whether mipmapping should be enabled on geometry rendered with this material
     */
    public Material(TerrainRenderPass pass, AlphaCutoffParameter alphaCutoff, boolean mipped) {
        this.pass = pass;
        this.packed = MaterialParameters.pack(alphaCutoff, mipped);

        this.alphaCutoff = alphaCutoff;
        this.mipped = mipped;
    }

    /**
     * {@return the packed representation of this Material to be encoded in vertex data}
     */
    public int bits() {
        return this.packed;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Material material = (Material) o;
        return packed == material.packed && pass.equals(material.pass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pass, packed);
    }
}
