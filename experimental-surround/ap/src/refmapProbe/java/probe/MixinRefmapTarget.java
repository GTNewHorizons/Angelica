package probe;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RefmapTarget.class)
class MixinRefmapTarget {
    @Surround(method = "run()V")
    private void wholeMethod() {}

    @Surround.Finally
    private void always() {}

    @Surround(method = "run(Ljava/lang/String;)V", id = "call", at = @At(value = "INVOKE", target = "Lprobe/RefmapTarget;helper()V"))
    private void callForm() {}

    @Surround.Finally("call")
    private void alwaysCall() {}
}
