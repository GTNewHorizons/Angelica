package com.gtnewhorizons.angelica.mixins.early.angelica.chat;

import com.gtnewhorizons.angelica.mixins.interfaces.ChatLineFormattedAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiNewChat.class)
public class MixinGuiNewChat {

    @WrapOperation(
        method = "drawChat",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/IChatComponent;getFormattedText()Ljava/lang/String;"))
    private String angelica$cacheFormattedText(IChatComponent component, Operation<String> original, @Local ChatLine line) {
        final long epoch = StatCollector.getLastTranslationUpdateTimeInMilliseconds();
        final ChatLineFormattedAccessor cache = (ChatLineFormattedAccessor) line;
        final String cached = cache.angelica$getFormatted(epoch);
        if (cached != null) return cached;
        final String formatted = original.call(component);
        cache.angelica$setFormatted(formatted, epoch);
        return formatted;
    }
}
