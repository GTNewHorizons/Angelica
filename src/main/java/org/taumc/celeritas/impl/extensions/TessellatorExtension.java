package org.taumc.celeritas.impl.extensions;

public interface TessellatorExtension {
    int[] celeritas$getRawBuffer();
    int celeritas$getVertexCount();
    void celeritas$reset();
}
