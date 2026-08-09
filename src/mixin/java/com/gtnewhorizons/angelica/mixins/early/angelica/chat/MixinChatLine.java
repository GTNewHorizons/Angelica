package com.gtnewhorizons.angelica.mixins.early.angelica.chat;

import com.gtnewhorizons.angelica.mixins.interfaces.ChatLineFormattedAccessor;
import net.minecraft.client.gui.ChatLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChatLine.class)
public class MixinChatLine implements ChatLineFormattedAccessor {

    @Unique private String angelica$formatted;
    @Unique private long angelica$formattedEpoch = Long.MIN_VALUE;

    @Override
    public String angelica$getFormatted(long epoch) {
        return angelica$formattedEpoch == epoch ? angelica$formatted : null;
    }

    @Override
    public void angelica$setFormatted(String formatted, long epoch) {
        angelica$formatted = formatted;
        angelica$formattedEpoch = epoch;
    }
}
