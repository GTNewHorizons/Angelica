package com.gtnewhorizons.angelica.rendering.celeritas;

import static com.mitchej123.lwjgl.LWJGLServiceProvider.LWJGL;
import static com.mitchej123.lwjgl.LWJGLServiceProvider.POINTER_SIZE;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.DrawCommandList;
import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.tessellation.GlIndexType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.DrawCommandSink;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.MultiDrawEmitter;

public record IndividualDrawEmitter(MultiDrawBatch batch) implements MultiDrawEmitter {
    public IndividualDrawEmitter() {
        this(new MultiDrawBatch(MAX_COMMAND_COUNT));
    }

    @Override
    public DrawCommandSink getCommandSink() {
        return this.batch;
    }

    @Override
    public void executeBatch(CommandList commandList, GlTessellation tessellation, GlPrimitiveType primitiveType) {
        try (DrawCommandList ignored = commandList.beginTessellating(tessellation)) {
            final int mode = primitiveType.getId();
            final int type = GlIndexType.UNSIGNED_INT.getFormatId();

            for (int i = 0; i < batch.size; i++) {
                final int count = LWJGL.memGetInt(batch.pElementCount + (long) i * Integer.BYTES);
                if (count > 0) {
                    LWJGL.glDrawElementsBaseVertex(mode, count, type, LWJGL.memGetAddress(batch.pElementPointer + (long) i * POINTER_SIZE), LWJGL.memGetInt(batch.pBaseVertex + (long) i * Integer.BYTES));
                }
            }
        }
    }

    @Override
    public void delete() {
        this.batch.delete();
    }
}
