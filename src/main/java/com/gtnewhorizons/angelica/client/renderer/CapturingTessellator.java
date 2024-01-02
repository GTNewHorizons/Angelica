package com.gtnewhorizons.angelica.client.renderer;

import com.gtnewhorizons.angelica.compat.mojang.VertexFormat;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.compat.nd.RecyclingList;
import lombok.Getter;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;

public class CapturingTessellator extends Tessellator {
    @Getter protected final ArrayList<Quad> quads = new ArrayList<>();
    protected final VertexFormat format;

    private final BlockRenderer.Flags FLAGS = new BlockRenderer.Flags(true, true, true, false);
    private final RecyclingList<Quad> quadBuf = new RecyclingList<>(Quad::new);
    public CapturingTessellator(VertexFormat format) {
        super();
        this.format = format;
    }

    @Override
    public int draw() {
        // Adapted from Neodymium

        FLAGS.hasBrightness = this.hasBrightness;
        FLAGS.hasColor = this.hasColor;

        final int verticesPerPrimitive = this.drawMode == GL11.GL_QUADS ? 4 : 3;

        for(int quadI = 0; quadI < this.vertexCount / verticesPerPrimitive; quadI++) {
            final Quad quad = quadBuf.next();
            quad.setState(this.rawBuffer, quadI * (verticesPerPrimitive * 8), FLAGS, this.drawMode, 0f, 0f, 0f);

            if(quad.deleted) {
                quadBuf.remove();
            }
        }
        quads.addAll(quadBuf.getAsList());

        final int i = this.rawBufferIndex * 4;
        this.reset();
        this.isDrawing = false;
        return i;
    }
}
