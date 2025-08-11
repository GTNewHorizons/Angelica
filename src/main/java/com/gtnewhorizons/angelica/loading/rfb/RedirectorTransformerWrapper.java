package com.gtnewhorizons.angelica.loading.rfb;

import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import com.gtnewhorizons.angelica.transform.RedirectorTransformer;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.jar.Manifest;

/** RFB wrapper for {@link com.gtnewhorizons.angelica.transform.RedirectorTransformer} */
public class RedirectorTransformerWrapper implements RfbClassTransformer {

    private final RedirectorTransformer inner = new RedirectorTransformer();

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
        return RedirectorTransformer.getTransformerExclusions();
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
        return RedirectorTransformer.shouldRfbTransform(classNode.getOriginalBytes());
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
