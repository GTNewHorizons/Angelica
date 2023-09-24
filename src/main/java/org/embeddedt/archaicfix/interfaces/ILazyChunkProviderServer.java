package org.embeddedt.archaicfix.interfaces;

public interface ILazyChunkProviderServer {
    boolean dropLazyChunk(int x, int z, Runnable runnable);
}
