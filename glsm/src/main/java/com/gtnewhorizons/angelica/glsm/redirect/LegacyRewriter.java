package com.gtnewhorizons.angelica.glsm.redirect;

import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

public interface LegacyRewriter {

    /** Replaces {@code call} in place and returns the replacement's name for the legacy warning, or null to leave it alone. */
    String rewrite(InsnList instructions, MethodInsnNode call);
}
