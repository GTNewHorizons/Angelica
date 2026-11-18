package com.gtnewhorizons.angelica.api.tesr;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class TesrShaders {

    private static final ConcurrentHashMap<String, TesrShader> REGISTRY = new ConcurrentHashMap<>();

    private TesrShaders() {}

    /** Registers a GL program under a unique name and returns its handle, wraps draws of any {@link TesrMaterial} referencing it */
    public static TesrShader register(String name, Runnable bind, Runnable release) {
        checkName(name);
        Objects.requireNonNull(bind, "bind");
        Objects.requireNonNull(release, "release");
        return add(new TesrShader(name, bind, release, null, null, null));
    }

    public static TesrShader register(String name, ResourceLocation vertex, ResourceLocation fragment) {
        return register(name, vertex, fragment, null);
    }

    /** Registers GLSL bodies (no {@code #version}) wrapped in Angelica's prelude, compiled at the first draw that binds them */
    public static TesrShader register(String name, ResourceLocation vertex, ResourceLocation fragment, @Nullable TesrShaderUniforms uniforms) {
        checkName(name);
        Objects.requireNonNull(vertex, "vertex");
        Objects.requireNonNull(fragment, "fragment");
        return add(new TesrShader(name, null, null, vertex, fragment, uniforms));
    }

    private static void checkName(String name) {
        final int colon = name.indexOf(':');
        if (colon <= 0 || colon == name.length() - 1 || name.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("TESR shader name must be namespace:path, got '" + name + "'");
        }
    }

    private static TesrShader add(TesrShader shader) {
        if (REGISTRY.putIfAbsent(shader.name(), shader) != null) {
            throw new IllegalStateException("TESR shader '" + shader.name() + "' already registered");
        }
        return shader;
    }
}
