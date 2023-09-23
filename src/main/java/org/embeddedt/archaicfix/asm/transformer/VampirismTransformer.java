package org.embeddedt.archaicfix.asm.transformer;

import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.Set;

import static org.objectweb.asm.Opcodes.ACC_TRANSIENT;
import static org.objectweb.asm.Opcodes.ASM5;
import static org.embeddedt.archaicfix.ArchaicLogger.LOGGER;

public class VampirismTransformer implements IClassTransformer {
    public static void init() {
        try {
            Set<String> transformerExceptions = ReflectionHelper.getPrivateValue(LaunchClassLoader.class, Launch.classLoader, "transformerExceptions");
            transformerExceptions.remove("de.teamlapen.vampirism");
        } catch(Exception e) {
            LOGGER.error("Failed to remove Vampirism from transformer exceptions.");
            e.printStackTrace();
        }
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if(transformedName.equals("de.teamlapen.vampirism.generation.castle.BlockList")) {
            LOGGER.info("Transforming " + transformedName + " to set Block field as transient");

            try {
                final ClassReader cr = new ClassReader(basicClass);
                final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

                final ClassNode cn = new ClassNode(ASM5);
                cr.accept(cn, 0);

                for (FieldNode fn : cn.fields) {
                    if (fn.name.equals("block") && fn.desc.equals("Lnet/minecraft/block/Block;")) {
                        fn.access |= ACC_TRANSIENT;
                    }
                }

                cn.accept(cw);
                return cw.toByteArray();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        return basicClass;
    }
}
