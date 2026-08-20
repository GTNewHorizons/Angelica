package com.gtnewhorizons.angelica.loading.rfb.transformers;

import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import com.gtnewhorizons.angelica.loading.shared.transformers.AngelicaRedirector;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.jar.Attributes;
import java.util.jar.Manifest;

/** RfbClassTransformer wrapper for {@link AngelicaRedirector} */
public class RFBAngelicaRedirector implements RfbClassTransformer {

    private final AngelicaRedirector inner;

    public RFBAngelicaRedirector() {
        inner = new AngelicaRedirector();
    }

    @Pattern("[a-z0-9-]+")
    @Override
    public @NotNull String id() {
        return "redirector";
    }

    private static final Attributes.Name LWJGL3_AWARE = new Attributes.Name("Lwjgl3ify-Aware");

    /** We sort before lwjgl3ify:redirect, so an aware caller's descriptors are still org.lwjgl here and stay that way. */
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
                                          @NotNull RfbClassTransformer.Context context, @Nullable Manifest manifest,
                                          @NotNull String className, @NotNull ClassNodeHandle classNode) {
        final boolean changed = inner.transformClassNode(className, classNode.getNode(), isLwjgl3Aware(manifest));
        if (changed) {
            classNode.computeMaxs();
            AngelicaClassDump.dumpRFBClass(className, classNode, this);
        }
        return changed;
    }

    @Override
    public void transformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull RfbClassTransformer.Context context,
                               @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        transformClassIfNeeded(classLoader, context, manifest, className, classNode);
    }
}
