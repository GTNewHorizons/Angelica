package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.rendering.tesr.EntityShadowReplay;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.entity.boss.IBossDisplayData;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BossStatus.class)
public class MixinBossStatus_EntityReplay {
    @Surround(method = "setBossStatus")
    private static void angelica$observeBossStatus() {
    }

    @Surround.Return
    private static void angelica$recordBossStatus(IBossDisplayData boss, boolean colorModifier) {
        EntityShadowReplay.recordBossStatus(boss, colorModifier);
    }
}
