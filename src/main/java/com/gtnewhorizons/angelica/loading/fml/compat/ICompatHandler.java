package com.gtnewhorizons.angelica.loading.fml.compat;

import java.util.List;
import java.util.Map;

public interface ICompatHandler {

    default Map<String, List<String>> getFieldLevelTessellator() {
        return null;
    }

    default Map<String, List<String>> getTileEntityNullGuard() {
        return null;
    }

    default Map<String, Boolean> getThreadSafeISBRHAnnotations() {
        return null;
    }

    default Map<String, List<String>> getHUDCachingEarlyReturn() {
        return null;
    }

    default List<String> extraTransformers() {
        return null;
    }

}
