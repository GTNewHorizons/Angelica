package com.gtnewhorizons.angelica.glsm.hooks;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface ShaderWorkSubmitter {
    <T> CompletableFuture<T> submit(Supplier<T> work);
}
