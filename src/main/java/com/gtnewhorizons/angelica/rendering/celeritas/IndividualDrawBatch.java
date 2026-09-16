package com.gtnewhorizons.angelica.rendering.celeritas;

import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL;
import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.POINTER_SIZE;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.DirectMultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.device.DrawCommandList;
import org.embeddedt.embeddium.impl.gl.tessellation.GlIndexType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;

public class IndividualDrawBatch extends DirectMultiDrawBatch {

    public IndividualDrawBatch(int capacity) {
        super(capacity);
    }

    @Override
    public void execute(CommandList commandList, GlTessellation tessellation, GlPrimitiveType primitiveType) {
        try (DrawCommandList ignored = commandList.beginTessellating(tessellation)) {
            final int mode = primitiveType.getId();
            final int type = GlIndexType.UNSIGNED_INT.getFormatId();
            final int commands = this.size();

            for (int i = 0; i < commands; i++) {
                final int count = LWJGL.memGetInt(this.pElementCount + (long) i * Integer.BYTES);
                LWJGL.glDrawElementsBaseVertex(mode, count, type, LWJGL.memGetAddress(this.pElementPointer + (long) i * POINTER_SIZE), LWJGL.memGetInt(this.pBaseVertex + (long) i * Integer.BYTES));
            }
        }
    }
}
