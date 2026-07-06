package com.gtnewhorizons.angelica.loading.fml.transformers;

import com.gtnewhorizons.angelica.loading.AngelicaClientTweaker;
import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import com.gtnewhorizons.angelica.loading.shared.transformers.IsbrhTessellatorAbuseTransform;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/** IClassTransformer wrapper for {@link IsbrhTessellatorAbuseTransform} */
public class IsbrhTessellatorAbuseTransformer implements IClassTransformer {

    private final IsbrhTessellatorAbuseTransform inner = new IsbrhTessellatorAbuseTransform();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!inner.shouldTransform(basicClass)) return basicClass;

        final ClassReader cr = new ClassReader(basicClass);
        final ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        if (!inner.transformClassNode(cn, AngelicaClientTweaker.isObfEnv())) return basicClass;

        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        final byte[] bytes = cw.toByteArray();
        AngelicaClassDump.dumpClass(transformedName, basicClass, bytes, this);
        return bytes;
    }
}
