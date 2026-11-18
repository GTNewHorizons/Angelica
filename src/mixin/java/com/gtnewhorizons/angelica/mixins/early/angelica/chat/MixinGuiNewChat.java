package com.gtnewhorizons.angelica.mixins.early.angelica.chat;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.mixins.interfaces.ChatLineFormattedAccessor;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.StatCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiNewChat.class)
public class MixinGuiNewChat {

    @Surround(
        method = "drawChat",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/IChatComponent;getFormattedText()Ljava/lang/String;"))
    private void angelica$cacheFormattedText(@Surround.Local ChatLine line) {
        @Surround.Carry
        long epoch = StatCollector.getLastTranslationUpdateTimeInMilliseconds();
        @Surround.Carry
        String cached = ((ChatLineFormattedAccessor) line).angelica$getFormatted(epoch);
        @Surround.Skip
        boolean skip = cached != null;
    }

    @Surround.Skipped
    private String angelica$cacheFormattedTextSkipped(@Surround.Carry String cached) {
        return cached;
    }

    @Surround.Return
    private String angelica$cacheFormattedTextStore(String formatted, @Surround.Local ChatLine line, @Surround.Carry long epoch) {
        ((ChatLineFormattedAccessor) line).angelica$setFormatted(formatted, epoch);
        return formatted;
    }
}
