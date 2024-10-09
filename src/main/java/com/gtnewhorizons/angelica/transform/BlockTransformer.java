package com.gtnewhorizons.angelica.transform;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;

public class BlockTransformer implements IClassTransformer {

    private static final String BlockClassFriendly = "net.minecraft.block.Block";
    private static final List<String> mcpMapping = ImmutableList.of(
        "minX",
        "minY",
        "minZ",
        "maxX",
        "maxY",
        "maxZ");
    private static final List<String> srgMapping = ImmutableList.of(
        "field_149759_B",
        "field_149760_C",
        "field_149754_D",
        "field_149755_E",
        "field_149756_F",
        "field_149757_G");

    public static List<String> getBlockBoundsFields() {
        return AngelicaTweaker.isObfEnv() ? srgMapping : mcpMapping;
    }

    public static String getClearFieldName(String name) {
        return AngelicaTweaker.isObfEnv() ? mcpMapping.get(srgMapping.indexOf(name)) : name;
    }

    /**
     * Delete the global vanilla bounding box fields off the Block object. {@link RedirectorTransformer}
     * replaces these with a thread-safe alternative.
     */
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass != null && BlockClassFriendly.equals(transformedName)) {
            final ClassReader cr = new ClassReader(basicClass);
            final ClassNode cn = new ClassNode();
            cr.accept(cn, 0);
            cn.fields.removeIf(field -> getBlockBoundsFields().contains(field.name));
            ClassWriter cw = new ClassWriter(0);
            cn.accept(cw);
            final byte[] bytes = cw.toByteArray();
            AngelicaTweaker.dumpClass(transformedName, basicClass, bytes, this);
            return bytes;
        }
        return basicClass;
    }

}
