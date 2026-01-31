package net.coderbot.iris.pipeline.transform;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gl.texture.TextureType;
import net.coderbot.iris.helpers.Tri;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import net.coderbot.iris.pipeline.transform.parameter.CeleritasTerrainParameters;
import net.coderbot.iris.pipeline.transform.parameter.ComputeParameters;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import net.coderbot.iris.pipeline.transform.parameter.TextureStageParameters;
import net.coderbot.iris.shaderpack.texture.TextureStage;

import java.util.LinkedHashMap;
import java.util.Map;

public class TransformPatcher {

    private static final int MAX_CACHE_ENTRIES = 400;
    private static final Map<TransformPatcher.CacheKey, Map<PatchShaderType, String>> cache = new LinkedHashMap<>(MAX_CACHE_ENTRIES + 1, .75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, Map<PatchShaderType, String>> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    private static final boolean useCache = true;

    private static class CacheKey {
        final Parameters parameters;
        final String vertex;
        final String geometry;
        final String tessControl;
        final String tessEval;
        final String fragment;

        public CacheKey(Parameters parameters, String vertex, String geometry, String tessControl, String tessEval, String fragment) {
            this.parameters = parameters;
            this.vertex = vertex;
            this.geometry = geometry;
            this.tessControl = tessControl;
            this.tessEval = tessEval;
            this.fragment = fragment;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((fragment == null) ? 0 : fragment.hashCode());
            result = prime * result + ((geometry == null) ? 0 : geometry.hashCode());
            result = prime * result + ((tessControl == null) ? 0 : tessControl.hashCode());
            result = prime * result + ((tessEval == null) ? 0 : tessEval.hashCode());
            result = prime * result + ((parameters == null) ? 0 : parameters.hashCode());
            result = prime * result + ((vertex == null) ? 0 : vertex.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            final TransformPatcher.CacheKey other = (TransformPatcher.CacheKey) obj;
            return java.util.Objects.equals(fragment, other.fragment)
                && java.util.Objects.equals(geometry, other.geometry)
                && java.util.Objects.equals(tessControl, other.tessControl)
                && java.util.Objects.equals(tessEval, other.tessEval)
                && java.util.Objects.equals(parameters, other.parameters)
                && java.util.Objects.equals(vertex, other.vertex);
        }
    }

    private static Map<PatchShaderType, String> transform(String vertex, String geometry, String tessControl, String tessEval, String fragment, Parameters parameters) {
        if (vertex == null && geometry == null && tessControl == null && tessEval == null && fragment == null) {
            return null;
        }

        // check if this has been cached
        TransformPatcher.CacheKey key = null;
        Map<PatchShaderType, String> result = null;
        if (useCache) {
            key = new TransformPatcher.CacheKey(parameters, vertex, geometry, tessControl, tessEval, fragment);
            synchronized (cache) {
                result = cache.get(key);
            }
        }

        // if there is no cache result, transform the shaders
        if (result == null) {
            result = ShaderTransformer.transform(vertex, geometry, tessControl, tessEval, fragment, parameters);
            if (useCache) {
                synchronized (cache) {
                    // Double-check in case another thread added it while we were transforming
                    final Map<PatchShaderType, String> existing = cache.get(key);
                    if (existing != null) {
                        return existing;
                    }
                    cache.put(key, result);
                }
            }
        }

        return result;
    }

    public static Map<PatchShaderType, String> patchAttributes(String vertex, String geometry, String tessControl, String tessEval, String fragment, InputAvailability inputs) {
        return transform(vertex, geometry, tessControl, tessEval, fragment, new AttributeParameters(Patch.ATTRIBUTES, geometry != null, inputs));
    }

    public static Map<PatchShaderType, String> patchAttributes(String vertex, String geometry, String fragment, InputAvailability inputs) {
        return patchAttributes(vertex, geometry, null, null, fragment, inputs);
    }

    public static Map<PatchShaderType, String> patchCeleritasTerrain(String vertex, String geometry, String fragment) {
        return transform(vertex, geometry, null, null, fragment, new CeleritasTerrainParameters(Patch.CELERITAS_TERRAIN));
    }

    public static Map<PatchShaderType, String> patchComposite(String vertex, String geometry, String fragment) {
        return patchComposite(vertex, geometry, null, null, fragment, TextureStage.COMPOSITE_AND_FINAL, null);
    }

    public static Map<PatchShaderType, String> patchComposite(String vertex, String geometry, String tessControl, String tessEval, String fragment) {
        return patchComposite(vertex, geometry, tessControl, tessEval, fragment, TextureStage.COMPOSITE_AND_FINAL, null);
    }

    public static Map<PatchShaderType, String> patchComposite(String vertex, String geometry, String tessControl, String tessEval, String fragment, TextureStage stage, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
        return transform(vertex, geometry, tessControl, tessEval, fragment, new TextureStageParameters(Patch.COMPOSITE, stage, textureMap));
    }

    public static Map<PatchShaderType, String> patchComposite(String vertex, String geometry, String fragment, TextureStage stage, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
        return patchComposite(vertex, geometry, null, null, fragment, stage, textureMap);
    }

    public static String patchCompute(String name, String compute, TextureStage stage, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
        if (compute == null) {
            return null;
        }
        Map<PatchShaderType, String> result = ShaderTransformer.transformCompute(compute, new ComputeParameters(Patch.COMPUTE, stage, textureMap));
        return result != null ? result.get(PatchShaderType.COMPUTE) : null;
    }

    public static void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
    }
}
