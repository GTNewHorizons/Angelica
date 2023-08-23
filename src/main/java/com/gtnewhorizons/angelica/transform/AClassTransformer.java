package com.gtnewhorizons.angelica.transform;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;

public class AClassTransformer implements IClassTransformer {

    /** map of class transformer */
    protected Map<String, IClassTransformer> ctMap;

    public void put(Names.Clas clas, IClassTransformer ct) {
        ctMap.put(clas.clas.replace('/', '.'), ct);
    }

    public AClassTransformer() {
        InitNames.init();
        ctMap = new HashMap<>();
        put(Names.entityRenderer_, new ACTEntityRenderer());
        put(Names.rendererLivingE_, new ACTRendererLivingEntity());
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        byte[] bytecode = basicClass;
        IClassTransformer ct = ctMap.get(transformedName);
        if (ct != null) {
            bytecode = ct.transform(name, transformedName, bytecode);
            // HACK: Fix stackframes
            ClassNode node = new ClassNode();
            ClassReader reader = new ClassReader(bytecode);
            reader.accept(node, ClassReader.SKIP_FRAMES);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            node.accept(writer);
            bytecode = writer.toByteArray();
            // END HACK
            int oldLength = basicClass.length;
            int newLength = bytecode.length;
            AngelicaTweaker.LOGGER.debug(" {} (+{})", newLength, newLength - oldLength);
        }
        return bytecode;
    }
}
