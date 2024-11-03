package com.gtnewhorizons.angelica.transform.compat.handlers;

import com.gtnewhorizons.angelica.transform.compat.CompatHandlerVisitor;

import java.util.List;
import java.util.Map;

public interface CompatHandler {

    default void accept(CompatHandlerVisitor visitor) {
        visitor.visit(this);
    }

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
