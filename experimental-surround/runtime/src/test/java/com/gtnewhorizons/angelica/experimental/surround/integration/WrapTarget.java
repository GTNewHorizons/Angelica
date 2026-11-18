package com.gtnewhorizons.angelica.experimental.surround.integration;

import com.gtnewhorizons.angelica.experimental.surround.integration.CallTarget.Callee;
import com.gtnewhorizons.angelica.experimental.surround.targets.Trace;

public class WrapTarget {

    private final Callee callee = new Callee();

    public int wrapped(int a) {
        Trace.add("body " + a);
        return a * 2;
    }

    public int originalOperands(int a) {
        return this.callee.twice(a);
    }

    public int shifted(int a) {
        final int local = a + 1;
        final String label = "L" + local;
        return this.callee.twice(local) + label.length();
    }
}
