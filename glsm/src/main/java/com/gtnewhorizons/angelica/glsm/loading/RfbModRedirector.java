package com.gtnewhorizons.angelica.glsm.loading;

import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.jar.Attributes;
import java.util.jar.Manifest;

public abstract class RfbModRedirector implements RfbClassTransformer {

    private static final Attributes.Name LWJGL3_AWARE = new Attributes.Name("Lwjgl3ify-Aware");

    private final String id;
    private final ModRedirector inner;

    protected RfbModRedirector(@Pattern("[a-z0-9-]+") String id, ModRedirector inner) {
        this.id = id;
        this.inner = inner;
    }

    @Override
    public @NotNull String id() {
        return id;
    }

    private static boolean isLwjgl3Aware(@Nullable Manifest manifest) {
        return manifest != null && "true".equals(manifest.getMainAttributes().getValue(LWJGL3_AWARE));
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
    public boolean transformClassIfNeeded(@NotNull ExtensibleClassLoader classLoader,
        @NotNull RfbClassTransformer.Context context, @Nullable Manifest manifest, @NotNull String className,
        @NotNull ClassNodeHandle classNode) {
        final boolean changed = inner.transformClassNode(className, classNode.getNode(), isLwjgl3Aware(manifest));
        if (changed) {
            classNode.computeMaxs();
            inner.getDump().dumpRFBClass(className, classNode, this);
        }
        return changed;
    }

    @Override
    public void transformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull RfbClassTransformer.Context context,
        @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        transformClassIfNeeded(classLoader, context, manifest, className, classNode);
    }
}
