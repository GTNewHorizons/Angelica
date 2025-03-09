package org.embeddedt.embeddium.impl.gl.tessellation;

import org.embeddedt.embeddium.impl.gl.device.CommandList;

public interface GlTessellation {
    void delete(CommandList commandList);

    void bind(CommandList commandList);

    void unbind(CommandList commandList);
}
