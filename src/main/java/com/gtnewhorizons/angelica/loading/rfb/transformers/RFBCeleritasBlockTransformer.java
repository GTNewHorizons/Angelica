package com.gtnewhorizons.angelica.loading.rfb.transformers;

import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import com.gtnewhorizons.angelica.loading.shared.transformers.CeleritasBlockTransform;
import com.gtnewhorizons.angelica.loading.shared.transformers.TileEntityMarkerTransform;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassHeaderMetadata;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;

import java.util.jar.Manifest;

public class RFBCeleritasBlockTransformer implements RfbClassTransformer {

    private final CeleritasBlockTransform inner;
    private final TileEntityMarkerTransform tileEntities;

    public RFBCeleritasBlockTransformer(boolean isObf) {
        inner = new CeleritasBlockTransform(isObf);
        tileEntities = new TileEntityMarkerTransform(isObf);
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

        final String thisName = metadata.binaryThisName();
        final String superName = metadata.binarySuperName();
        final byte[] originalBytes = classNode.getOriginalBytes();

        inner.trackBlockSubclasses(thisName, superName);
        if (context == RfbClassTransformer.Context.LCL_WITH_TRANSFORMS) {
            tileEntities.track(thisName, superName);
            if (tileEntities.markersFor(thisName, originalBytes) != 0) return true;
        }
        return inner.shouldTransform(originalBytes);
    }

    @Override
    public boolean transformClassIfNeeded(@NotNull ExtensibleClassLoader classLoader,
                                          @NotNull RfbClassTransformer.Context context, @Nullable Manifest manifest,
                                          @NotNull String className, @NotNull ClassNodeHandle classNode) {
        final ClassNode node = classNode.getNode();
        boolean changed = false;
        if (context == RfbClassTransformer.Context.LCL_WITH_TRANSFORMS) {
            final int markers = tileEntities.markersFor(node.name, classNode.getOriginalBytes());
            if (markers != 0) {
                tileEntities.addMarkers(node, markers);
                changed = true;
            }
        }
        changed |= inner.transformClassNode(className, node);
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
