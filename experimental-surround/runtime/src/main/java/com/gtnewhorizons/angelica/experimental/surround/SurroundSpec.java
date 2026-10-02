package com.gtnewhorizons.angelica.experimental.surround;

import org.spongepowered.asm.lib.Type;
import org.spongepowered.asm.lib.tree.AnnotationNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;
import org.spongepowered.asm.util.Annotations;

interface SurroundSpec {

    String SURROUND = Type.getDescriptor(Surround.class);
    String CATCH = Type.getDescriptor(Surround.Catch.class);
    String FINALLY = Type.getDescriptor(Surround.Finally.class);
    String CARRY = Type.getDescriptor(Surround.Carry.class);
    String SKIP = Type.getDescriptor(Surround.Skip.class);
    String SKIPPED = Type.getDescriptor(Surround.Skipped.class);
    String RETURN = Type.getDescriptor(Surround.Return.class);
    String LOCAL = Type.getDescriptor(Surround.Local.class);
    String MIXIN_MERGED = Type.getDescriptor(MixinMerged.class);
    String OBJECT = Type.getInternalName(Object.class);
    String THROWABLE = Type.getInternalName(Throwable.class);
    String THROWABLE_DESC = Type.getDescriptor(Throwable.class);

    static String mergedBy(MethodNode method) {
        final AnnotationNode merged = Annotations.get(method.visibleAnnotations, MIXIN_MERGED);
        return merged == null ? null : Annotations.<String>getValue(merged, "mixin");
    }

    static String describeOwner(MethodNode method) {
        final String owner = mergedBy(method);
        return owner == null ? "not merged from a mixin" : "merged from " + owner;
    }
}
