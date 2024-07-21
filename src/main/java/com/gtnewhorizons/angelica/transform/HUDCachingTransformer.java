package com.gtnewhorizons.angelica.transform;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.apache.commons.io.FileUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;


public class HUDCachingTransformer implements IClassTransformer {
    static final String HUDCaching = "com/gtnewhorizons/angelica/hudcaching/HUDCaching$HUDCachingHooks";
    private static final boolean DUMP_CLASSES = Boolean.parseBoolean(System.getProperty("angelica.dumpClass", "false"));

    static final Map<String, List<String>> ReturnEarlyMethods = ImmutableMap.of(
        "thaumcraft.client.lib.RenderEventHandler", ImmutableList.of("renderOverlay"),
        "com.kentington.thaumichorizons.client.lib.RenderEventHandler", ImmutableList.of("renderOverlay")
    );

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        if (ReturnEarlyMethods.containsKey(transformedName)) {
            ClassReader cr = new ClassReader(basicClass);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            for (String targetMethod : ReturnEarlyMethods.get(transformedName)) {
                for (MethodNode method : cn.methods) {
                    if (!method.name.equals(targetMethod)) continue;

                    InsnList list = new InsnList();
                    LabelNode exitLabel = new LabelNode();
                    AngelicaTweaker.LOGGER.info("Injecting HUDCaching Conditional Return: " + transformedName + "#" + method.name);
                    list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HUDCaching, "shouldReturnEarly", "()Z", false));
                    list.add(new JumpInsnNode(Opcodes.IFEQ, exitLabel));
                    if (method.desc.endsWith("Z") || method.desc.endsWith("I")) {
                        list.add(new InsnNode(Opcodes.ICONST_0));
                        list.add(new InsnNode(Opcodes.IRETURN));
                    } else {
                        list.add(new InsnNode(Opcodes.RETURN));
                    }
                    list.add(exitLabel);
                    method.instructions.insert(list);
                }
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            final byte[] bytes = cw.toByteArray();
            saveTransformedClass(bytes, transformedName);
            return bytes;
        }

        return basicClass;
    }

    private File outputDir = null;
    private void saveTransformedClass(final byte[] data, final String transformedName) {
        if (!DUMP_CLASSES) {
            return;
        }
        if (outputDir == null) {
            outputDir = new File(Launch.minecraftHome, "ASM_REDIRECTOR");
            try {
                FileUtils.deleteDirectory(outputDir);
            } catch (IOException ignored) {}
            if (!outputDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                outputDir.mkdirs();
            }
        }
        final String fileName = transformedName.replace('.', File.separatorChar);
        final File classFile = new File(outputDir, fileName + ".class");
        final File outDir = classFile.getParentFile();
        if (!outDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            outDir.mkdirs();
        }
        if (classFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            classFile.delete();
        }
        try (final OutputStream output = Files.newOutputStream(classFile.toPath())) {
            output.write(data);
        } catch (IOException e) {
            AngelicaTweaker.LOGGER.error("Could not save transformed class (byte[]) " + transformedName, e);
        }
    }
}
