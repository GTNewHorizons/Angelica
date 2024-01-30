package com.gtnewhorizons.angelica.transform;

import com.google.common.collect.ImmutableList;
import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.tree.ClassNode;

import java.util.List;

public class BlockTransformer implements IClassTransformer {
    public static final String BlockClass = "net/minecraft/block/Block";
    public static final String BlockPackage = BlockClass.substring(0, BlockClass.lastIndexOf('/') + 1);
    private static final String BlockClassFriendly = BlockClass.replace('/', '.');
    public static final List<Pair<String, String>> BlockBoundsFields = ImmutableList.of(
        Pair.of("minX", "field_149759_B"),
        Pair.of("minY", "field_149760_C"),
        Pair.of("minZ", "field_149754_D"),
        Pair.of("maxX", "field_149755_E"),
        Pair.of("maxY", "field_149756_F"),
        Pair.of("maxZ", "field_149757_G")
    );

    /**
     * Delete the global vanilla bounding box fields off the Block object. {@link RedirectorTransformer}
     * replaces these with a thread-safe alternative.
     */
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if(basicClass != null && transformedName.equals(BlockClassFriendly)) {
            final ClassReader cr = new ClassReader(basicClass);
            final ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            cn.fields.removeIf(node -> BlockBoundsFields.stream().anyMatch(pair -> node.name.equals(pair.getLeft()) || node.name.equals(pair.getRight())));

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            basicClass = cw.toByteArray();
        }

        return basicClass;
    }
}
