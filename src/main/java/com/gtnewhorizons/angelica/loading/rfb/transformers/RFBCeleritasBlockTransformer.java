package com.gtnewhorizons.angelica.loading.rfb.transformers;

import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import com.gtnewhorizons.angelica.loading.shared.transformers.CeleritasBlockTransform;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassHeaderMetadata;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.jar.Manifest;

public class RFBCeleritasBlockTransformer implements RfbClassTransformer {

    private final CeleritasBlockTransform inner;

    public RFBCeleritasBlockTransformer(boolean isObf) {
        inner = new CeleritasBlockTransform(isObf);
    }

    @Pattern("[a-z0-9-]+")
    @Override
    public @NotNull String id() {
        return "sodiumblocktransform";
    }

    @Override
    public @NotNull String @Nullable [] sortAfter() {
        return new String[]{"*", "mixin:mixin"};
    }

    @Override
    public @NotNull String @Nullable [] sortBefore() {
        return new String[]{"lwjgl3ify:redirect"};
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

        ClassHeaderMetadata metadata = classNode.getOriginalMetadata();
        if (metadata == null) {
            return false;
        }

        if (inner.shouldTransform(classNode.getOriginalBytes())) {
            return true;
        }

        // Track possible block subclasses even if we don't need to transform this class
        inner.trackBlockSubclasses(metadata.binaryThisName, metadata.binarySuperName);

        return false;
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
