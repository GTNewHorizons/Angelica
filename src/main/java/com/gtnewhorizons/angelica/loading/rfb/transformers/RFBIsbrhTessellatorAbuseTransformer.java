package com.gtnewhorizons.angelica.loading.rfb.transformers;

import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import com.gtnewhorizons.angelica.loading.shared.transformers.IsbrhTessellatorAbuseTransform;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassHeaderMetadata;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.jar.Manifest;

/** RfbClassTransformer wrapper for {@link IsbrhTessellatorAbuseTransform} */
public class RFBIsbrhTessellatorAbuseTransformer implements RfbClassTransformer {

    private final IsbrhTessellatorAbuseTransform inner;
    private final boolean isObf;

    public RFBIsbrhTessellatorAbuseTransformer(boolean isObf) {
        this.inner = new IsbrhTessellatorAbuseTransform();
        this.isObf = isObf;
    }

    @Pattern("[a-z0-9-]+")
    @Override
    public @NotNull String id() {
        return "isbrh-tessellator-abuse";
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
    public boolean shouldTransformClass(@NotNull ExtensibleClassLoader classLoader,
        @NotNull RfbClassTransformer.Context context, @Nullable Manifest manifest, @NotNull String className,
        @NotNull ClassNodeHandle classNode) {
        if (!classNode.isPresent()) return false;
        final ClassHeaderMetadata metadata = classNode.getOriginalMetadata();
        return metadata != null && metadata.binaryInterfaceNames.contains(IsbrhTessellatorAbuseTransform.ISBRH);
    }

    @Override
    public void transformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull RfbClassTransformer.Context context,
        @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        if (inner.transformClassNode(classNode.getNode(), this.isObf)) {
            classNode.computeMaxs();
            AngelicaClassDump.dumpRFBClass(className, classNode, this);
        }
    }
}
