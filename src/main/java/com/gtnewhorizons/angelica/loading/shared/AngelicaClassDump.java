package com.gtnewhorizons.angelica.loading.shared;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.loading.ClassDump;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;

public class AngelicaClassDump {

    public static final ClassDump INSTANCE = new ClassDump(SystemProperties.DUMP_CLASS);

    public static void dumpClass(String className, byte[] originalBytes, byte[] transformedBytes, Object transformer) {
        INSTANCE.dumpClass(className, originalBytes, transformedBytes, transformer);
    }

    public static void dumpRFBClass(String className, ClassNodeHandle classNode, Object transformer) {
        INSTANCE.dumpRFBClass(className, classNode, transformer);
    }
}
