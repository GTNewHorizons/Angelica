package com.gtnewhorizons.angelica.glsm.loading;

import com.gtnewhorizon.gtnhlib.asm.ASMUtil;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;

public final class ClassDump {

    public static final ClassDump DISABLED = new ClassDump(false);

    private final boolean enabled;

    public ClassDump(boolean enabled) {
        this.enabled = enabled;
    }

    public void dumpClass(String className, byte[] originalBytes, byte[] transformedBytes, Object transformer) {
        if (!enabled) return;
        ASMUtil.saveAsRawClassFile(originalBytes, className + "_PRE", transformer);
        ASMUtil.saveAsRawClassFile(transformedBytes, className + "_POST", transformer);
    }

    public void dumpRFBClass(String className, ClassNodeHandle classNode, Object transformer) {
        if (!enabled) return;
        dumpClass(className, classNode.getOriginalBytes(), classNode.computeBytes(), transformer);
    }
}
