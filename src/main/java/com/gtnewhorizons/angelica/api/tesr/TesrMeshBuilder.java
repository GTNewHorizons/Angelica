package com.gtnewhorizons.angelica.api.tesr;

/** Emits local-space geometry into the sink's buckets. Prefer implementing {@link TesrMeshProvider}. */
@FunctionalInterface
public interface TesrMeshBuilder {

    void angelica$build(TesrMeshSink sink);
}
