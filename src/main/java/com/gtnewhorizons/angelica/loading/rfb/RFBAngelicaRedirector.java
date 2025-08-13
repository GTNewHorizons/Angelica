package com.gtnewhorizons.angelica.loading.rfb;

import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import com.gtnewhorizons.angelica.loading.shared.AngelicaRedirector;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.jar.Manifest;

/** RfbClassTransformer wrapper for {@link AngelicaRedirector} */
public class RFBAngelicaRedirector implements RfbClassTransformer {

    private final AngelicaRedirector inner = new AngelicaRedirector();

    @Pattern("[a-z0-9-]+")
    @Override
    public @NotNull String id() {
        return "redirector";
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
        if (!classNode.isOriginal()) {
            // If a class is already a transformed ClassNode, conservatively continue processing.
            return true;
        }
        return inner.shouldTransform(classNode.getOriginalBytes());
    }

    @Override
    public void transformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull RfbClassTransformer.Context context,
        @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        final boolean changed = inner.transformClassNode(className, classNode.getNode());
        if (changed) {
            classNode.computeMaxs();
            AngelicaClassDump.dumpRFBClass(className, classNode, this);
        }
    }
}
