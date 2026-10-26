package com.gtnewhorizons.angelica.loading.fml.compat.transformers.specific;

import com.gtnewhorizons.angelica.loading.AngelicaClientTweaker;
import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import net.minecraft.launchwrapper.IClassTransformer;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;

public class KaizPatchXTransformer implements IClassTransformer {

    private static final String ANGELICA_COMPAT = "jp.kaiz.kaizpatch.compat.AngelicaCompat";
    private static final String SCRIPT_DESC = "(Ljava/lang/String;)Ljava/lang/String;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        if (!transformedName.equals(ANGELICA_COMPAT)) return basicClass;

        final ClassReader cr = new ClassReader(basicClass);
        final ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        MethodNode target = null;
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("transformScript") && mn.desc.equals(SCRIPT_DESC)) {
                target = mn;
                break;
            }
        }
        if (target == null) {
            AngelicaClientTweaker.LOGGER.info("[AngelicaCompat]Extra Transformers: KaizPatchXTransformer found no transformScript in {}", transformedName);
            return basicClass;
        }

        for (AbstractInsnNode in : target.instructions.toArray()) {
            if (in.getOpcode() == Opcodes.ARETURN) {
                target.instructions.insertBefore(
                    in,
                    new MethodInsnNode(Opcodes.INVOKESTATIC, "com/gtnewhorizons/angelica/api/GLRedirects", "redirectScript", SCRIPT_DESC, false));
            }
        }

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        final byte[] bytes = cw.toByteArray();
        AngelicaClientTweaker.LOGGER.info("[AngelicaCompat]Extra Transformers: Applied KaizPatchXTransformer");
        AngelicaClassDump.dumpClass(transformedName, basicClass, bytes, this);
        return bytes;
    }
}
