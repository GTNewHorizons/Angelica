package com.gtnewhorizons.angelica.compat.nd.writers;

import com.gtnewhorizons.angelica.api.QuadView;
import com.gtnewhorizons.angelica.compat.nd.IWriteQuads;

import java.nio.ByteBuffer;

public class PositionQuadWriter implements IWriteQuads {
    private boolean direct;

    public PositionQuadWriter() {
        init(false);
    }

    public void init(boolean direct) {
        // This would need to be re-initialized if the direct vs indirect changes
        this.direct = direct;
    }
    @Override
    public void writeQuad(QuadView quad, ByteBuffer buf) {
        if(direct) {
            writeQuadDirect(quad, buf);
        } else {
            writeQuadIndirect(quad, buf);
        }
    }

    protected  void writeQuadDirect(QuadView quad, ByteBuffer buf) {
        throw new UnsupportedOperationException("Direct mode not supported yet");
    }

    protected void writeQuadIndirect(QuadView quad, ByteBuffer buf) {
        for(int idx = 0; idx < 4; ++idx) {
            // Position
            buf.putFloat(quad.getX(idx));
            buf.putFloat(quad.getY(idx));
            buf.putFloat(quad.getZ(idx));
        }
    }
}
