package com.gtnewhorizons.angelica.loading.rfb.transformers;

import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import com.gtnewhorizons.angelica.loading.shared.transformers.DarkModeFontTransform;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.jar.Manifest;

public class RFBDarkModeFontTransformer implements RfbClassTransformer {

    private final DarkModeFontTransform inner;
    private final boolean isObf;

    public RFBDarkModeFontTransformer(boolean isObf) {
        this.inner = new DarkModeFontTransform();
        this.isObf = isObf;
    }

    @Pattern("[a-z0-9-]+")
    @Override
    public @NotNull String id() {
        return "dark-mode-font";
    }

    @Override
    public boolean shouldTransformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull RfbClassTransformer.Context context,
                                        @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        if (!classNode.isPresent()) {
            return false;
        }

        for (String targetClass : DarkModeFontTransform.classesToTransform) {
            if (className.equals(targetClass)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean transformClassIfNeeded(@NotNull ExtensibleClassLoader classLoader, @NotNull RfbClassTransformer.Context context,
                                          @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        final boolean changed = inner.transformClassNode(classNode.getNode(), className, this.isObf);
        if (changed) {
            classNode.computeMaxs();
            classNode.computeFrames();
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
