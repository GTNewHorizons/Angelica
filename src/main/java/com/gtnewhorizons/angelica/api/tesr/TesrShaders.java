package com.gtnewhorizons.angelica.api.tesr;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class TesrShaders {

    private static final ConcurrentHashMap<String, TesrShader> REGISTRY = new ConcurrentHashMap<>();

    private TesrShaders() {}

    /**  Registers a GL program under a unique name and returns its handle, wraps draws of any {@link TesrMaterial} referencing it */
    public static TesrShader register(String name, Runnable bind, Runnable release) {
        final int colon = name.indexOf(':');
        if (colon <= 0 || colon == name.length() - 1 || name.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("TESR shader name must be namespace:path, got '" + name + "'");
        }
        Objects.requireNonNull(bind, "bind");
        Objects.requireNonNull(release, "release");
        final TesrShader shader = new TesrShader(name, bind, release);
        if (REGISTRY.putIfAbsent(name, shader) != null) {
            throw new IllegalStateException("TESR shader '" + name + "' already registered");
        }
        return shader;
    }
}
