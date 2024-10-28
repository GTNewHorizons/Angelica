package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;

import java.util.LinkedHashMap;
import java.util.Map;

public class TransformPatcher {

    private static final int MAX_CACHE_ENTRIES = 400;
    private static final Map<TransformPatcher.CacheKey, Map<PatchShaderType, String>> cache =  new LinkedHashMap<>(MAX_CACHE_ENTRIES + 1, .75F, true) {
        public boolean removeEldestEntry(Map.Entry eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    private static final boolean useCache = true;

    private static class CacheKey {
        final Parameters parameters;
        final String vertex;
        final String geometry;
        final String fragment;

        public CacheKey(Parameters parameters, String vertex, String geometry, String fragment) {
            this.parameters = parameters;
            this.vertex = vertex;
            this.geometry = geometry;
            this.fragment = fragment;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((fragment == null) ? 0 : fragment.hashCode());
            result = prime * result + ((geometry == null) ? 0 : geometry.hashCode());
            result = prime * result + ((parameters == null) ? 0 : parameters.hashCode());
            result = prime * result + ((vertex == null) ? 0 : vertex.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            TransformPatcher.CacheKey other = (TransformPatcher.CacheKey) obj;
            if (fragment == null) {
                if (other.fragment != null)
                    return false;
            } else if (!fragment.equals(other.fragment))
                return false;
            if (geometry == null) {
                if (other.geometry != null)
                    return false;
            } else if (!geometry.equals(other.geometry))
                return false;
            if (parameters == null) {
                if (other.parameters != null)
                    return false;
            } else if (!parameters.equals(other.parameters))
                return false;
            if (vertex == null) {
                if (other.vertex != null)
                    return false;
            } else if (!vertex.equals(other.vertex))
                return false;
            return true;
        }
    }

    private static Map<PatchShaderType, String> transform(String vertex, String geometry, String fragment, Parameters parameters) {
        if (vertex == null && geometry == null && fragment == null) {
            return null;
        }

        // check if this has been cached
        TransformPatcher.CacheKey key;
        Map<PatchShaderType, String> result = null;
        if (useCache) {
            key = new TransformPatcher.CacheKey(parameters, vertex, geometry, fragment);
            if (cache.containsKey(key)) {
                result = cache.get(key);
            }
        }

        // if there is no cache result, transform the shaders
        if (result == null) {
            result = ShaderTransformer.transform(vertex, geometry, fragment, parameters);
            if (useCache) {
                cache.put(key, result);
            }
        }

        return result;
    }

    public static Map<PatchShaderType, String> patchAttributes(String vertex, String geometry, String fragment, InputAvailability inputs) {
        return transform(vertex, geometry, fragment, new AttributeParameters(Patch.ATTRIBUTES, geometry != null, inputs));
    }

    public static Map<PatchShaderType, String> patchSodiumTerrain(String vertex, String geometry, String fragment) {
        return transform(vertex, geometry, fragment, new Parameters(Patch.SODIUM_TERRAIN));
    }

    public static Map<PatchShaderType, String> patchComposite(String vertex, String geometry, String fragment) {
        return transform(vertex, geometry, fragment, new Parameters(Patch.COMPOSITE));
    }
}
