package com.gtnewhorizons.angelica.transform;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;

public class AsmUtil {

    public static String obf(String deobf, String obf) {
        if (AngelicaTweaker.isObfEnv()) {
            return obf;
        }
        return deobf;
    }
}
