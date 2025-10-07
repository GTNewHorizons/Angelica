package com.gtnewhorizons.angelica.compat.nd.writers;


import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadView;
import com.gtnewhorizon.gtnhlib.client.renderer.quad.writers.IWriteQuads;

import java.nio.ByteBuffer;

public class PositionTextureColorQuadWriter implements IWriteQuads {
    private boolean direct;

    public PositionTextureColorQuadWriter() {
        init(false);
    }

    public void init(boolean direct) {
        // This would need to be re-initialized if the direct vs indirect changes
        this.direct = direct;
    }
    @Override
    public void writeQuad(ModelQuadView quad, ByteBuffer buf) {
        if(direct) {
            writeQuadDirect(quad, buf);
        } else {
            writeQuadIndirect(quad, buf);
        }
    }

    protected  void writeQuadDirect(ModelQuadView quad, ByteBuffer buf) {
        throw new UnsupportedOperationException("Direct mode not supported yet");
    }

    protected void writeQuadIndirect(ModelQuadView quad, ByteBuffer buf) {
        for(int idx = 0; idx < 4; ++idx) {
            // Position
            buf.putFloat(quad.getX(idx));
            buf.putFloat(quad.getY(idx));
            buf.putFloat(quad.getZ(idx));

            // Texture
            buf.putFloat(quad.getTexU(idx));
            buf.putFloat(quad.getTexV(idx));

            // Color
            buf.putInt(quad.getColor(idx));

        }
    }
}
