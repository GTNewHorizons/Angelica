package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import net.minecraft.client.renderer.entity.Render;
import org.spongepowered.asm.mixin.Mixin;

// The label sits above the model and its text is deferred, so its background quad does not make the batcher demote it.
@Mixin(Render.class)
public abstract class MixinRender_NameTagDraws {

    @Surround(method = "func_147906_a")
    private void angelica$countNameTagDraws() {
        BatchEligibility.beginExpectedDraws(GLStateManager.drawCalls);
    }

    @Surround.Finally
    private void angelica$endNameTagDraws() {
        BatchEligibility.endExpectedDraws(GLStateManager.drawCalls);
    }
}
