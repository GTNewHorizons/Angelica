package com.gtnewhorizons.angelica.loading.fml.transformers;

import com.gtnewhorizons.angelica.loading.AngelicaClientTweaker;
import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import com.gtnewhorizons.angelica.loading.shared.transformers.CeleritasBlockTransform;
import com.gtnewhorizons.angelica.loading.shared.transformers.TileEntityMarkerTransform;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/** IClassTransformer wrapper for {@link CeleritasBlockTransform} */
public class CeleritasBlockTransformer implements IClassTransformer {

    private final CeleritasBlockTransform inner;
    private final TileEntityMarkerTransform tileEntities;
    private final String[] exclusions;

    public CeleritasBlockTransformer() {
        this.inner = new CeleritasBlockTransform(AngelicaClientTweaker.isObfEnv());
        this.tileEntities = new TileEntityMarkerTransform(AngelicaClientTweaker.isObfEnv());
        this.exclusions = inner.getTransformerExclusions();
    }

    /**
     * Delete the global vanilla bounding box fields off the Block object. {@link CeleritasBlockTransform}
     * replaces these with a thread-safe alternative.
     */
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        for (String exclusion : exclusions) {
            if (transformedName.startsWith(exclusion)) return basicClass;
        }

        final ClassReader cr = new ClassReader(basicClass);
        final String className = cr.getClassName();
        inner.trackBlockSubclasses(className, cr.getSuperName());
        tileEntities.track(className, cr.getSuperName());

        final int markers = tileEntities.markersFor(className, basicClass);
        final byte[] marked = markers == 0 ? basicClass : tileEntities.addMarkers(basicClass, markers);

        if (!inner.shouldTransform(marked)) {
            return marked;
        }

        final ClassNode cn = new ClassNode();
        (markers == 0 ? cr : new ClassReader(marked)).accept(cn, 0);
        final boolean changed = inner.transformClassNode(transformedName, cn);
        if (changed) {
            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            final byte[] bytes = cw.toByteArray();
            AngelicaClassDump.dumpClass(transformedName, basicClass, bytes, this);
            return bytes;
        }
        return marked;
    }

}
