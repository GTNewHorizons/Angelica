package org.embeddedt.archaicfix.asm.transformer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.launchwrapper.IClassTransformer;
import org.embeddedt.archaicfix.ArchaicLogger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ASM5;

public class ThreadedBlockTransformer implements IClassTransformer {
    private static final Set<String> threadedFields = ImmutableSet.of(
            "minX",
            "minY",
            "maxX",
            "maxY",
            "minZ",
            "maxZ"
    );
    private static final Map<String, String> threadedObfFields = ImmutableMap.<String, String>builder()
            .put("field_149759_B", "minX")
            .put("field_149760_C", "minY")
            .put("field_149755_E", "maxX")
            .put("field_149756_F", "maxY")
            .put("field_149754_D", "minZ")
            .put("field_149757_G", "maxZ")
            .build();
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        /* FIXME this doesn't work outside dev at the moment */
        if(true)
            return basicClass;
        if(!transformedName.startsWith("org.embeddedt.archaicfix")) {
            final ClassReader cr = new ClassReader(basicClass);
            final ClassWriter cw = new ClassWriter(0);

            final ClassNode cn = new ClassNode(ASM5);
            cr.accept(cn, 0);
            for (MethodNode m : cn.methods) {
                ListIterator<AbstractInsnNode> insns = m.instructions.iterator();
                boolean transformed = false;
                while(insns.hasNext()) {
                    AbstractInsnNode node = insns.next();
                    if(node.getOpcode() == Opcodes.GETFIELD || node.getOpcode() == Opcodes.PUTFIELD) {
                        boolean isSetter = node.getOpcode() == Opcodes.PUTFIELD;
                        FieldInsnNode f = (FieldInsnNode)node;
                        if(f.owner.equals("net/minecraft/block/Block")) {
                            boolean obfContains = threadedObfFields.containsKey(f.name);
                            boolean devContains = threadedFields.contains(f.name);
                            if(obfContains || devContains) {
                                transformed = true;
                                ArchaicLogger.LOGGER.info("Transforming threaded block data access in {}.{}()", transformedName, m.name);
                                f.owner = "org/embeddedt/archaicfix/block/ThreadedBlockData";
                                if(obfContains)
                                    f.name = threadedObfFields.get(f.name);
                                insns.previous();
                                if(isSetter) {
                                    /* FIXME: assumes a double is at the top of the stack */
                                    insns.add(new InsnNode(Opcodes.DUP2_X1));
                                    insns.add(new InsnNode(Opcodes.POP2));
                                }
                                String m_name = "arch$getThreadedData";
                                String m_desc = "()Lorg/embeddedt/archaicfix/block/ThreadedBlockData;";
                                insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/block/Block", m_name, m_desc, false));
                                if(isSetter) {
                                    insns.add(new InsnNode(Opcodes.DUP_X2));
                                    insns.add(new InsnNode(Opcodes.POP));
                                }
                                insns.next();
                                insns.next();
                            }
                        }
                    }
                }
            }
            cn.accept(cw);
            return cw.toByteArray();
        }
        return basicClass;
    }
}
