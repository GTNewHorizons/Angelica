package org.embeddedt.archaicfix.ducks;

public interface ILazyChunkProviderServer {
    boolean dropLazyChunk(int x, int z, Runnable runnable);
}
