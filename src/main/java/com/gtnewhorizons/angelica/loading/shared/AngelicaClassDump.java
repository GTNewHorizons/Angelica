package com.gtnewhorizons.angelica.loading.shared;

import com.gtnewhorizon.gtnhlib.asm.ASMUtil;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;

public class AngelicaClassDump {

    public static void dumpClass(String className, byte[] originalBytes, byte[] transformedBytes, Object transformer) {
        if (SystemProperties.DUMP_CLASS) {
            ASMUtil.saveAsRawClassFile(originalBytes, className + "_PRE", transformer);
            ASMUtil.saveAsRawClassFile(transformedBytes, className + "_POST", transformer);
        }
    }

    public static void dumpRFBClass(String className, ClassNodeHandle classNode, Object transformer) {
        if (SystemProperties.DUMP_CLASS) {
            final byte[] originalBytes = classNode.getOriginalBytes();
            final byte[] transformedBytes = classNode.computeBytes();
            ASMUtil.saveAsRawClassFile(originalBytes, className + "_PRE", transformer);
            ASMUtil.saveAsRawClassFile(transformedBytes, className + "_POST", transformer);
        }
    }
}
