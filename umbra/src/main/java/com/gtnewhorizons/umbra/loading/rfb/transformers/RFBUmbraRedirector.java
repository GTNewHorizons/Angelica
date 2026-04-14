package com.gtnewhorizons.umbra.loading.rfb.transformers;

import com.gtnewhorizons.umbra.loading.shared.UmbraClassDump;
import com.gtnewhorizons.umbra.loading.shared.transformers.UmbraRedirector;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.jar.Manifest;

/** RfbClassTransformer wrapper for {@link UmbraRedirector} */
public class RFBUmbraRedirector implements RfbClassTransformer {

    private final UmbraRedirector inner;

    public RFBUmbraRedirector() {
        inner = new UmbraRedirector();
    }

    @Pattern("[a-z0-9-]+")
    @Override
    public @NotNull String id() {
        return "umbra-redirector";
    }

    @Override
    public @NotNull String @Nullable [] sortAfter() {
        return new String[] {"*", "mixin:mixin"};
    }

    @Override
    public @NotNull String @Nullable [] sortBefore() {
        return new String[] {"lwjgl3ify:redirect"};
    }

    @Override
    public @NotNull String @Nullable [] additionalExclusions() {
        return inner.getTransformerExclusions();
    }

    @Override
    public boolean shouldTransformClass(@NotNull ExtensibleClassLoader classLoader,
        @NotNull RfbClassTransformer.Context context, @Nullable Manifest manifest, @NotNull String className,
        @NotNull ClassNodeHandle classNode) {
        if (!classNode.isPresent()) {
            return false;
        }
        return inner.shouldTransform(classNode.getOriginalBytes());
    }

    @Override
    public void transformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull RfbClassTransformer.Context context,
        @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        final boolean changed = inner.transformClassNode(className, classNode.getNode());
        if (changed) {
            classNode.computeMaxs();
            UmbraClassDump.dumpRFBClass(className, classNode, this);
        }
    }
}
