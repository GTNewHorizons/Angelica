package com.gtnewhorizons.angelica.transform;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.apache.commons.io.FileUtils;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.InsnList;
import org.spongepowered.asm.lib.tree.InsnNode;
import org.spongepowered.asm.lib.tree.JumpInsnNode;
import org.spongepowered.asm.lib.tree.LabelNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.transformers.MixinClassWriter;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;


public class HUDCachingTransformer implements IClassTransformer, Opcodes {
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
                    list.add(new MethodInsnNode(INVOKESTATIC, HUDCaching, "shouldReturnEarly", "()Z", false));
                    list.add(new JumpInsnNode(IFEQ, exitLabel));
                    if (method.desc.endsWith("Z") || method.desc.endsWith("I")) {
                        list.add(new InsnNode(ICONST_0));
                        list.add(new InsnNode(IRETURN));
                    } else if (method.desc.endsWith("V")) {
                        list.add(new InsnNode(RETURN));
                    } else {
                        AngelicaTweaker.LOGGER.warn("HUDCaching Conditional Return - Unknown return type: " + transformedName + "#" + method.name + ":" + method.desc);
                        return basicClass;
                    }
                    list.add(exitLabel);
                    method.instructions.insert(list);
                }
            }

            MixinClassWriter cw = new MixinClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
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
            outputDir = new File(Launch.minecraftHome, "HUD_CACHING");
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
