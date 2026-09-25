package com.gtnewhorizons.angelica.loading.fml.transformers;

import com.gtnewhorizons.angelica.loading.AngelicaClientTweaker;
import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import com.gtnewhorizons.angelica.loading.shared.transformers.DarkModeFontTransform;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public class DarkModeFontTransformer implements IClassTransformer {

    private final DarkModeFontTransform inner = new DarkModeFontTransform();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (DarkModeFontTransform.classesToTransform.containsKey(transformedName)) {
            final ClassReader cr = new ClassReader(basicClass);
            final ClassNode cn = new ClassNode();
            cr.accept(cn, 0);
            if (!inner.transformClassNode(cn, transformedName, AngelicaClientTweaker.isObfEnv())) {
                return basicClass;
            }
            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            final byte[] bytes = cw.toByteArray();
            AngelicaClassDump.dumpClass(transformedName, basicClass, bytes, this);
            return bytes;
        }
        return basicClass;
    }
}
