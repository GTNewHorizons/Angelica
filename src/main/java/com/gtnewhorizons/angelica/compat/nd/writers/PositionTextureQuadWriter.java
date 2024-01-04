package com.gtnewhorizons.angelica.compat.nd.writers;

import com.gtnewhorizons.angelica.compat.nd.IWriteQuads;
import com.gtnewhorizons.angelica.compat.nd.Quad;

import java.nio.ByteBuffer;

public class PositionTextureQuadWriter implements IWriteQuads {
    private boolean direct;

    public PositionTextureQuadWriter() {
        init(false);
    }

    public void init(boolean direct) {
        // This would need to be re-initialized if the direct vs indirect changes
        this.direct = direct;
    }
    @Override
    public void writeQuad(Quad quad, ByteBuffer buf) {
        if(direct) {
            writeQuadDirect(quad, buf);
        } else {
            writeQuadIndirect(quad, buf);
        }
    }

    protected  void writeQuadDirect(Quad quad, ByteBuffer buf) {
        throw new UnsupportedOperationException("Direct mode not supported yet");
    }

    protected void writeQuadIndirect(Quad quad, ByteBuffer buf) {
        for(int idx = 0; idx < 4; ++idx) {
            // Position
            buf.putFloat(quad.getX(idx));
            buf.putFloat(quad.getY(idx));
            buf.putFloat(quad.getZ(idx));

            // Texture
            buf.putFloat(quad.getTexU(idx));
            buf.putFloat(quad.getTexV(idx));
        }
    }
}
