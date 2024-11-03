package com.gtnewhorizons.angelica.transform.compat;

import com.gtnewhorizons.angelica.transform.compat.handlers.CompatHandler;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
public class CompatRegistry implements CompatHandlerVisitor {

    public static final CompatRegistry INSTANCE = new CompatRegistry();

    private final List<CompatHandler> handlers = new ArrayList<>();

    private final Map<String, List<String>> fieldLevelTessellatorTransforms = new Object2ObjectOpenHashMap<>();
    private final Map<String, List<String>> tileEntityNullGuardTransforms = new Object2ObjectOpenHashMap<>();
    private final Map<String, Boolean> threadSafeISBRHAnnotations = new Object2BooleanOpenHashMap<>();
    private final Map<String, List<String>> hudCachingEarlyReturns = new Object2ObjectOpenHashMap<>();

    public void register(CompatHandler handler) {
        handler.accept(this);
        this.handlers.add(handler);
    }

    @Override
    public void visit(CompatHandler handler) {
        if (handler.getFieldLevelTessellator() != null)
            this.fieldLevelTessellatorTransforms.putAll(handler.getFieldLevelTessellator());
        if (handler.getTileEntityNullGuard() != null)
            this.tileEntityNullGuardTransforms.putAll(handler.getTileEntityNullGuard());
        if (handler.getThreadSafeISBRHAnnotations() != null)
            this.threadSafeISBRHAnnotations.putAll(handler.getThreadSafeISBRHAnnotations());
        if (handler.getHUDCachingEarlyReturn() != null)
            this.hudCachingEarlyReturns.putAll(handler.getHUDCachingEarlyReturn());
    }

}
