package com.gtnewhorizons.angelica.experimental.surround.integration.mixin;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.experimental.surround.integration.WholeTarget;
import com.gtnewhorizons.angelica.experimental.surround.targets.Trace;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = WholeTarget.class, priority = 1100, remap = false)
public class MixinWholeTargetHigh {

    @Surround(method = "priority(I)I")
    private void surround$enterHigh(int a) {
        Trace.add("enterHigh");
    }

    @Surround.Return
    private int surround$returnHigh(int result) {
        Trace.add("returnHigh " + result);
        return result * 10;
    }

    @Surround.Finally
    private void surround$exitHigh() {
        Trace.add("exitHigh");
    }
}
